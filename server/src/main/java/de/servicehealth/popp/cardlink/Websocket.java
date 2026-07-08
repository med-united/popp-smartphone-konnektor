package de.servicehealth.popp.cardlink;

import de.gematik.ws.conn.cardservice.wsdl.v8_2.NewAPDUForSession;
import de.gematik.ws.conn.observability.MetricsRegistry.Counters;
import de.servicehealth.popp.session.Store;
import de.servicehealth.popp.session.WebsocketEntry;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

@ServerEndpoint("/websocket/{tlsCertCN}")
@SuppressWarnings("unused")
public class Websocket {

  private final Store store;

  @Inject
  @SuppressWarnings("unused")
  public Websocket(Store store) {
    this.store = store;
  }

  private enum MessageTypes {
    REGISTER_EGK("registerEGK"),
    SEND_APDU_RESPONSE("sendAPDUResponse"),
    EREZEPT_TOKENS("eRezeptTokensFromAVS"),
    EREZEPT_BUNDLES("eRezeptBundlesFromAVS");

    private final String type;

    MessageTypes(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    @Nullable
    public static MessageTypes fromType(String name) {
      return Arrays.stream(MessageTypes.values())
          .filter(type -> type.getType().equals(name))
          .findFirst()
          .orElse(null);
    }
  }

  private record WebsocketMessage(
      MessageTypes type,
      String message,
      String cardSessionId,
      Session websocketSession,
      String payload,
      String correlationId,
      String tlsCertCn) {}

  private static final Logger LOG = Logger.getLogger(Websocket.class.getName());

  @OnOpen
  public void onOpen(Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {
    Counters.WEBSOCKETS_OPENED.increment();
    // Handle new connection
    LOG.info("WebSocket opened for tlsCertCN: " + tlsCertCN);
    // Add session to store
    store.addEntry(new WebsocketEntry(tlsCertCN, webSocketSession));

    // send pin to websocket client
    webSocketSession.getAsyncRemote().sendText("123456");
  }

  @OnMessage
  @SuppressWarnings("unused")
  public void refactoredOnMessage(
      String message, Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {

    var websocketMessageOpt = extractWebsocketMessage(message, webSocketSession, tlsCertCN);

    if (websocketMessageOpt.isEmpty()) {
      return;
    }

    var websocketMessage = websocketMessageOpt.get();
    switch (websocketMessage.type) {
      case REGISTER_EGK -> handleRegisterEgk(websocketMessage);
      case SEND_APDU_RESPONSE -> handleSendApduResponse(websocketMessage);
      case EREZEPT_BUNDLES, EREZEPT_TOKENS -> handleErezept(websocketMessage);
    }
  }

  private JsonObject parseEGKPayload(byte[] payloadBytes) {
    try {
      return Json.createReader(new ByteArrayInputStream(payloadBytes)).readObject();
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to parse RegisterEgkPayload JSON", e);
      return null;
    }
  }

  @OnClose
  public void onClose(Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {
    Counters.WEBSOCKETS_CLOSED.increment();
    store.removeEntryBySessionId(webSocketSession.getId());
    LOG.info("WebSocket closed for tlsCertCN: " + tlsCertCN);
  }

  public void onNewAPDUForSession(@Observes NewAPDUForSession event) {
    WebsocketEntry entry = store.findEntry(event.tlsCertCN, event.cardSessionId).orElseThrow();
    entry.startApduSession();
  }

  private void handleRegisterEgk(WebsocketMessage websocketMessage) {
    Counters.CARDS_REGISTERED.increment();
    store.registerEGK(
        websocketMessage.tlsCertCn(),
        websocketMessage.websocketSession(),
        parseEGKPayload(Base64.getDecoder().decode(websocketMessage.payload())),
        websocketMessage.cardSessionId());
  }

  private void handleSendApduResponse(WebsocketMessage websocketMessage) {

    Optional<WebsocketEntry> entry =
        store.findEntry(websocketMessage.tlsCertCn(), websocketMessage.cardSessionId());

    if (entry.isEmpty()) {
      LOG.warning(
          "No entry found for tlsCertCN and cardSessionId: "
              + websocketMessage.tlsCertCn()
              + ", "
              + websocketMessage.cardSessionId());
      return;
    }

    if (Objects.isNull(websocketMessage.cardSessionId())) {
      LOG.warning("No correlationId provided for sendAPDUResponse");
      return;
    }

    LOG.fine("Completing future for correlationId: " + websocketMessage.correlationId());
    byte[] decodedPayload = Base64.getDecoder().decode(websocketMessage.payload());
    JsonObject sendAPDUResponse =
        Json.createReader(new ByteArrayInputStream(decodedPayload)).readObject();

    String response = sendAPDUResponse.getString("response");

    String hex = HexFormat.of().formatHex(Base64.getDecoder().decode(response));
    LOG.fine("Decoded APDU response hex: " + hex);

    entry.get().completeApduResponse(websocketMessage.correlationId(), hex);
  }

  private void handleErezept(WebsocketMessage websocketMessage) {
    if (websocketMessage.type == MessageTypes.EREZEPT_BUNDLES) {
      Counters.PRESCRIPTIONS_RETURNED.increment();
    }
    var payload = parseEGKPayload(Base64.getDecoder().decode(websocketMessage.payload()));

    assert payload != null;
    var ctId = String.valueOf(payload.get("ctId")).replaceAll("\"", "");
    LOG.fine("Step eRezeptTokensFromAVS. Looking for card with id: " + ctId);
    Optional<WebsocketEntry> entry = store.findEntry(ctId);
    entry.ifPresent(websocketEntry -> websocketEntry.sendText(websocketMessage.message()));
  }

  private Optional<WebsocketMessage> extractWebsocketMessage(
      String message, Session webSocketSession, String tlsCertCN) {
    LOG.fine("Received message from " + tlsCertCN + ": " + message);
    // Echo the message back
    JsonObject jsonObject;
    JsonArray jsonArray;
    try {
      JsonStructure jsonStructure =
          Json.createReader(new ByteArrayInputStream(message.getBytes())).read();
      if (jsonStructure instanceof JsonArray jsonArrayInner) {
        try {
          jsonObject = jsonArrayInner.getJsonObject(0);
        } catch (IndexOutOfBoundsException | ClassCastException exception) {
          LOG.warning("Received JSON array does not contain a json Object");
          return Optional.empty();
        }
        jsonArray = jsonArrayInner;
      } else {
        LOG.warning("Received JSON is not an array but is: " + jsonStructure.getClass().getName());
        return Optional.empty();
      }

    } catch (JsonException | IllegalStateException exception) {
      LOG.fine("Could not parse websocket Json Message. Dropping message");
      return Optional.empty();
    }

    if (jsonArray.isEmpty()) {
      LOG.warning("Received JSON array is empty");
      return Optional.empty();
    }
    if (jsonArray.size() < 2) {
      LOG.warning("Received JSON array has no cardSessionId");
      return Optional.empty();
    }

    String cardSessionId;
    try {
      cardSessionId = jsonArray.getString(1);
    } catch (Exception e) {
      cardSessionId = "";
    }

    String correlationId = null;
    if (jsonArray.size() < 3) {
      LOG.warning("Received JSON array has no correlationId");
    } else {
      correlationId = jsonArray.getString(2); // correlationId
      LOG.fine("Correlation ID: " + correlationId);
    }

    String type;
    try {
      type = jsonObject.getString("type");
    } catch (NullPointerException | ClassCastException exception) {
      LOG.warning("Received JSON object does not have a type");
      return Optional.empty();
    }

    var messageType = MessageTypes.fromType(type);
    if (Objects.isNull(messageType)) {
      return Optional.empty();
    }

    String payload;
    try {
      payload = jsonObject.getString("payload");
    } catch (NullPointerException | ClassCastException exception) {
      LOG.warning("Received JSON object does not have a payload");
      return Optional.empty();
    }

    return Optional.of(
        new WebsocketMessage(
            messageType,
            message,
            cardSessionId,
            webSocketSession,
            payload,
            correlationId,
            tlsCertCN));
  }
}
