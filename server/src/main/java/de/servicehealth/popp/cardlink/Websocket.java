package de.servicehealth.popp.cardlink;

import de.gematik.ws.conn.cardservice.wsdl.v8_2.NewAPDUForSession;
import de.gematik.ws.conn.observability.MetricsRegistry;
import de.gematik.ws.conn.observability.MetricsRegistry.Counters;
import de.servicehealth.popp.session.Store;
import de.servicehealth.popp.session.WebsocketEntry;
import io.quarkus.logging.Log;
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

@ServerEndpoint("/websocket/{tlsCertCN}")
public class Websocket {

  private final Store store;
  private final MetricsRegistry metricsRegistry;

  @Inject
  public Websocket(Store store, MetricsRegistry metricsRegistry) {
    this.store = store;
    this.metricsRegistry = metricsRegistry;
  }

  private enum MessageTypes {
    REGISTER_EGK("registerEgk"),
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
    // Handle new connection
    LOG.info("WebSocket opened for tlsCertCN: " + tlsCertCN);
    // Add session to store
    store.addEntry(new WebsocketEntry(tlsCertCN, webSocketSession));

    // send pin to websocket client
    webSocketSession.getAsyncRemote().sendText("123456");
  }

  @OnMessage
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

  @OnMessage
  public void onMessage(
      String message, Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {
    // Handle incoming message
    LOG.fine("Received message from " + tlsCertCN + ": " + message);
    // Echo the message back
    try {
      JsonStructure jsonStructure =
          Json.createReader(new ByteArrayInputStream(message.getBytes())).read();
      if (!(jsonStructure instanceof JsonArray)) {
        LOG.warning(
            "Received JSON is not an array but is: "
                + (jsonStructure == null ? "null" : jsonStructure.getClass().getName()));
        return;
      }
      JsonArray jsonArray = (JsonArray) jsonStructure;
      if (jsonArray.size() < 1) {
        LOG.warning("Received JSON array is empty");
        return;
      }
      if (jsonArray.size() < 2) {
        LOG.warning("Received JSON array has no cardSessionId");
        return;
      }
      JsonObject jsonObject = jsonArray.getJsonObject(0);
      String cardSessionId; // cardSessionId
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

      if (jsonObject.containsKey("type")) {
        String type = jsonObject.getString("type");
        LOG.info("Message type: " + type);
        if (type.equals("registerEGK")) {
          // Handle registerEGK message
          store.registerEGK(
              tlsCertCN,
              webSocketSession,
              parseEGKPayload(Base64.getDecoder().decode(jsonObject.getString("payload"))),
              cardSessionId);
        } else if (type.equals("sendAPDUResponse")) {
          // Handle sendAPDUResponse message
          Optional<WebsocketEntry> entry = store.findEntry(tlsCertCN, cardSessionId);
          if (entry.isPresent()) {
            if (correlationId != null) {
              LOG.fine("Completing future for correlationId: " + correlationId);
              byte[] decodedPayload = Base64.getDecoder().decode(jsonObject.getString("payload"));
              JsonObject sendAPDUResponse =
                  Json.createReader(new ByteArrayInputStream(decodedPayload)).readObject();

              String response = sendAPDUResponse.getString("response");

              String hex = HexFormat.of().formatHex(Base64.getDecoder().decode(response));
              LOG.fine("Decoded APDU response hex: " + hex);

              entry.get().completeApduResponse(correlationId, hex);
            } else {
              LOG.warning("No correlationId provided for sendAPDUResponse");
            }
          } else {
            LOG.warning(
                "No entry found for tlsCertCN and cardSessionId: "
                    + tlsCertCN
                    + ", "
                    + cardSessionId);
          }
        } else if (type.equals("eRezeptTokensFromAVS") || type.equals("eRezeptBundlesFromAVS")) {
          var payload =
              parseEGKPayload(Base64.getDecoder().decode(jsonObject.getString("payload")));

          var ctId = String.valueOf(payload.get("ctId")).replaceAll("\"", "");
          Log.info("Step eRezeptTokensFromAVS. Looking for card with id: " + ctId);
          Optional<WebsocketEntry> entry = store.findEntry(ctId);
          entry.ifPresent(websocketEntry -> websocketEntry.sendText(message));
        }
      } else {
        LOG.fine("No type field in message");
      }
      // webSocketSession.getBasicRemote().sendText("Echo from " + tlsCertCN + ": " + message);
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Error sending message: " + e.getMessage(), e);
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
    var payload = parseEGKPayload(Base64.getDecoder().decode(websocketMessage.payload()));

    var ctId = String.valueOf(payload.get("ctId")).replaceAll("\"", "");
    Log.info("Step eRezeptTokensFromAVS. Looking for card with id: " + ctId);
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
      LOG.warning("Received JSON object's message type is unknown");
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
            tlsCertCN,
            correlationId));
  }
}
