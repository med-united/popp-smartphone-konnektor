package de.gematik.ws.conn.cardservice.wsdl.v8_2;

import de.gematik.ws.conn.cardservice.v8_2_1.ChangePin;
import de.gematik.ws.conn.cardservice.v8_2_1.DisablePin;
import de.gematik.ws.conn.cardservice.v8_2_1.EnablePin;
import de.gematik.ws.conn.cardservice.v8_2_1.GetPinStatus;
import de.gematik.ws.conn.cardservice.v8_2_1.GetPinStatusResponse;
import de.gematik.ws.conn.cardservice.v8_2_1.SecureSendAPDU;
import de.gematik.ws.conn.cardservice.v8_2_1.SecureSendAPDUResponse;
import de.gematik.ws.conn.cardservice.v8_2_1.SignedScenarioResponseType;
import de.gematik.ws.conn.cardservice.v8_2_1.StartCardSession;
import de.gematik.ws.conn.cardservice.v8_2_1.StartCardSessionResponse;
import de.gematik.ws.conn.cardservice.v8_2_1.StopCardSession;
import de.gematik.ws.conn.cardservice.v8_2_1.StopCardSessionResponse;
import de.gematik.ws.conn.cardservice.v8_2_1.UnblockPin;
import de.gematik.ws.conn.cardservice.v8_2_1.VerifyPin;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import de.servicehealth.cardlink.model.SendApduPayload;
import de.servicehealth.popp.session.ApduScenarioInitilizedEvent;
import de.servicehealth.popp.session.Store;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.json.bind.JsonbBuilder;
import jakarta.jws.WebService;
import jakarta.websocket.Session;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebService(
    serviceName = "CardService",
    portName = "CardServicePort",
    targetNamespace = "http://ws.gematik.de/conn/CardService/WSDL/v8.2",
    wsdlLocation = "classpath:/wsdl/CardService_v8_2_1.wsdl",
    endpointInterface = "de.gematik.ws.conn.cardservice.wsdl.v8_2.CardServicePortType")
public class CardServicePortImpl implements CardServicePortType {
  @Inject Store store;

  @Inject SecurityIdentity identity; // Inject the SecurityIdentity

  @Inject Event<SendApduPayloadWithSession> sendApduPayloadWithSessionEvent;

  @Inject Event<NewAPDUForSession> newAPDUForSessionEvent;

  @Inject Event<ApduScenarioInitilizedEvent> apduScenarioInitilizedEventEvent;

  private static final Logger LOG = Logger.getLogger(CardServicePortImpl.class.getName());

  @Override
  public SecureSendAPDUResponse secureSendAPDU(SecureSendAPDU parameter) throws FaultMessage {
    LOG.log(Level.FINE, "Executing secureSendAPDU");

    String signedScenarioJwt = parameter.getSignedScenario();

    Jwt<?, ?> jwt =
        Jwts.parser()
            .keyLocator(
                new Locator<Key>() {

                  @Override
                  public Key locate(Header header) {
                    return getKeyFromFirstEntry(header.get("x5c"));
                  }

                  private Key getKeyFromFirstEntry(Object object) {
                    if (object instanceof List) {
                      List<?> list = (List<?>) object;
                      if (list.size() > 0) {
                        Object firstEntry = list.get(0);
                        if (firstEntry instanceof String) {
                          String certBase64 = (String) firstEntry;
                          try {
                            InputStream is =
                                new ByteArrayInputStream(Base64.getDecoder().decode(certBase64));
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                            return cert.getPublicKey();
                          } catch (Exception e) {
                            LOG.log(Level.SEVERE, "Failed to parse certificate from x5c", e);
                          }
                        }
                      }
                    }
                    return null;
                  }
                })
            .build()
            .parse(signedScenarioJwt);
    Map<String, Object> standardScenarioMessage =
        (Map<String, Object>) ((Claims) jwt.getPayload()).get("message");
    String sessionId = (String) standardScenarioMessage.get("clientSessionId");
    // Find session in card sessions
    // Session session = store.getSessionForCardHandle(tlsCertCN, sessionId);
    var entry =
        store
            .findEntry(sessionId)
            .orElseThrow(() -> new FaultMessage("No session found for card handle: " + sessionId));
    var backMappedCertCN = entry.getTlsCertCN();
    var session = entry.getSession();

    newAPDUForSessionEvent.fire(new NewAPDUForSession(session, sessionId, backMappedCertCN));
    // Process APDU commands from standardScenarioMessage
    var adpuPayloads = new ArrayList<String>();
    for (Map<String, Object> step :
        (List<Map<String, Object>>) standardScenarioMessage.get("steps")) {
      String commandApduHex = (String) step.get("commandApdu");
      // Here you would send the APDU command to the card and get the response
      // For demonstration, we will just log the command
      LOG.fine("Processing APDU Command: " + commandApduHex);
      adpuPayloads.add(sendCardlinkWebsocketMessage(commandApduHex, sessionId));
    }

    entry.initializeApduScenario(adpuPayloads);
    apduScenarioInitilizedEventEvent.fire(new ApduScenarioInitilizedEvent(entry));

    SecureSendAPDUResponse response = new SecureSendAPDUResponse();
    response.setSignedScenarioResponse(new SignedScenarioResponseType());
    response
        .getSignedScenarioResponse()
        .setResponseApduList(new SignedScenarioResponseType.ResponseApduList());

    List<String> websocketResponses;
    try {
      websocketResponses =
          store.getAPDUResponses(backMappedCertCN, sessionId).get(10000, TimeUnit.MILLISECONDS);
      LOG.fine("Websocket Responses: ");
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.log(Level.SEVERE, "Error getting APDU response for session: " + sessionId, e);
      throw new FaultMessage("Error getting APDU response: " + e.getMessage());
    } finally {
      entry.clearApduScenario();
    }

    for (var websocketResponse : websocketResponses) {
      response
          .getSignedScenarioResponse()
          .getResponseApduList()
          .getResponseApdu()
          .add(websocketResponse);
    }

    LOG.fine("Collected all APDUs sending answer for session: " + sessionId);

    return response;
  }

  private String sendCardlinkWebsocketMessage(String apduCommandHex, String cardSessionId) {
    // Create SendApduEnvelope message
    SendApduPayload sendApduPayload = new SendApduPayload();
    sendApduPayload.setApdu(
        Base64.getEncoder().encodeToString(HexFormat.of().parseHex(apduCommandHex)));
    sendApduPayload.cardSessionId(cardSessionId);
    return Base64.getEncoder()
        .encodeToString(JsonbBuilder.create().toJson(sendApduPayload).getBytes());
  }

  @Override
  public PinResponseType unblockPin(UnblockPin parameter) throws FaultMessage {
    return null;
  }

  @Override
  public PinResponseType disablePin(DisablePin parameter) throws FaultMessage {
    return null;
  }

  @Override
  public PinResponseType enablePin(EnablePin parameter) throws FaultMessage {
    return null;
  }

  @Override
  public StartCardSessionResponse startCardSession(StartCardSession parameter) throws FaultMessage {
    LOG.fine("Executing startCardSession");
    String sessionId = UUID.randomUUID().toString();

    // find TLS Cert CN from security context
    String tlsCertCN = getTlsCertCN();

    // find existing entry in store
    Session session = store.getSessionForCardHandle(tlsCertCN, parameter.getCardHandle());

    if (session == null) {
      throw new FaultMessage("Can not establish session with card: " + parameter.getCardHandle());
    }

    // create new entry if not exists
    // store.getCardSessions().put(parameter.getCardHandle(), session);

    // Create and return a StartCardSessionResponse
    StartCardSessionResponse response = new StartCardSessionResponse();
    response.setSessionId(parameter.getCardHandle());

    // response.setSessionId("537e7eb7-82cd-4af0-90f2-3e514109f542");
    return response;
  }

  @Override
  public PinResponseType changePin(ChangePin parameter) throws FaultMessage {
    return null;
  }

  @Override
  public GetPinStatusResponse getPinStatus(GetPinStatus parameter) throws FaultMessage {
    return null;
  }

  @Override
  public StopCardSessionResponse stopCardSession(StopCardSession parameter) throws FaultMessage {
    // store.removeEntryBySessionId(parameter.getSessionId());
    // Create and return a StopCardSessionResponse
    StopCardSessionResponse response = new StopCardSessionResponse();
    response.setStatus(new Status());
    response.getStatus().setResult("OK");
    return response;
  }

  @Override
  public PinResponseType verifyPin(VerifyPin parameter) throws FaultMessage {
    return null;
  }

  private String getTlsCertCN() {
    String tlsCertCN = identity.getPrincipal().getName();
    tlsCertCN = tlsCertCN.replace("CN=", "");
    if ("".equals(tlsCertCN) || tlsCertCN == null) {
      tlsCertCN = "null";
    }
    return tlsCertCN;
  }
}
