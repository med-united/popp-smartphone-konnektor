package de.gematik.ws.conn.cardservice.wsdl.v8_2;

import de.gematik.ws.conn.cardservice.v8_2_1.*;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.servicehealth.cardlink.model.SendApduEnvelope;
import de.servicehealth.cardlink.model.SendApduPayload;
import de.servicehealth.cardlink.model.SendApduEnvelope.TypeEnum;
import de.servicehealth.popp.model.ScenarioStep;
import de.servicehealth.popp.model.SignedScenarioClaims;
import de.servicehealth.popp.model.StandardScenarioMessage;
import de.servicehealth.popp.session.Store;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JweHeader;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.websocket.Session;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.Charset;
import java.security.Key;

@jakarta.jws.WebService(serviceName = "CardService", portName = "CardServicePort", targetNamespace = "http://ws.gematik.de/conn/CardService/WSDL/v8.2", wsdlLocation = "classpath:/wsdl/CardService_v8_2_1.wsdl", endpointInterface = "de.gematik.ws.conn.cardservice.wsdl.v8_2.CardServicePortType")
public class CardServicePortImpl implements CardServicePortType {
	@Inject
	Store store;

	@Inject
    SecurityIdentity identity; // Inject the SecurityIdentity

	private static final Logger LOG = Logger.getLogger(CardServicePortImpl.class.getName());

	@Override
	public SecureSendAPDUResponse secureSendAPDU(SecureSendAPDU parameter) throws FaultMessage {
		LOG.log(Level.FINE, "Executing secureSendAPDU");
		
		// Find TLS Cert CN from security context
		String tlsCertCN = identity.getPrincipal().getName();

		String signedScenarioJwt = parameter.getSignedScenario();

		/*
		signedScenarioJwt contains a signed StandardScenarioMessage in JWT format.
		The JWT consists of three parts: header, claims (payload), and signature.
		The claims part contains the StandardScenarioMessage with details about the APDU commands to be executed.

		"signedScenario": "eyAB.cdEF.ghIJ"
		
		Where:

		Sample content of the signed `StandardScenarioMessage` (header, claims, signature):
        ```json
        {
          "typ": "JWT"
          "alg": "ES256"
          "x5c": ["MII..."]
          "stpl": "MII..."
        }
        .
        {
          "message": {
            "type": "StandardScenario",
            "version": "1.0.0",
            "clientSessionId": "123e4567-e89b-12d3-a456-426614174000",
            "sequenceCounter": 1,
            "timeSpan": 1000,
            "steps": [
              {
                "commandApdu": "00a4040c",
                "expectedStatusWords": ["9000", "6f00"]
              }
            ]
          }
        }
		*/
		LOG.info(signedScenarioJwt);
		Jwt<?, ?> jwt = Jwts.parser().keyLocator(new Locator<Key>() {

			@Override
			public Key locate(Header header) {
				return getKeyFromFirstEntry(header.get("x5c"));
			}

			private Key getKeyFromFirstEntry(Object object) {
				if(object instanceof List) {
					List<?> list = (List<?>) object;
					if(list.size() > 0) {
						Object firstEntry = list.get(0);
						if(firstEntry instanceof String) {
							String certBase64 = (String) firstEntry;
							try {
								InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(certBase64));
								java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
								java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) cf.generateCertificate(is);
								return cert.getPublicKey();
							} catch (Exception e) {
								LOG.log(Level.SEVERE, "Failed to parse certificate from x5c", e);
							}
						}
					}
				}
				return null;
			}
         
         }).build().parse(signedScenarioJwt);
		Map<String, Object> standardScenarioMessage = (Map<String, Object>) ((Claims) jwt.getPayload()).get("message");
		String sessionId = (String) standardScenarioMessage.get("clientSessionId");
		// Find session in card sessions
		Session session = store.getSessionForCardHandle(tlsCertCN, sessionId);
		if (session == null) {
			throw new FaultMessage("No session found for card handle: " + sessionId);
		}

		// Process APDU commands from standardScenarioMessage
		for (Map<String, Object> step : (List<Map<String, Object>>) standardScenarioMessage.get("steps")) {
			String commandApduHex = (String) step.get("commandApdu");
			// Here you would send the APDU command to the card and get the response
			// For demonstration, we will just log the command
			LOG.log(Level.FINE, "Processing APDU Command: " + commandApduHex);
			sendCardlinkWebsocketMessage(commandApduHex, session, sessionId);
		}

		SecureSendAPDUResponse response = new SecureSendAPDUResponse();

		return response;
	}

	private void sendCardlinkWebsocketMessage(String apduCommandHex, Session session, String cardSessionId) {
		// Create SendApduEnvelope message
		SendApduEnvelope envelope = new SendApduEnvelope();
		envelope.setType(TypeEnum.SEND_APDU);
		SendApduPayload sendApduPayload = new SendApduPayload();
		sendApduPayload.setApdu(HexFormat.of().parseHex(apduCommandHex));
		sendApduPayload.cardSessionId(cardSessionId);
		envelope.setPayload(sendApduPayload.toString().getBytes(Charset.defaultCharset()));

		// Send the message over WebSocket
		try {
			session.getBasicRemote().sendText(envelope.toString());
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Failed to send APDU command over WebSocket", e);
		}
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
		LOG.log(Level.INFO, "Executing startCardSession");
		String sessionId = UUID.randomUUID().toString();
		
		// find TLS Cert CN from security context
		String tlsCertCN = identity.getPrincipal().getName();

		// find existing entry in store
		Session session = store.getSessionForCardHandle(tlsCertCN, parameter.getCardHandle());

		// create new entry if not exists
		store.getCardSessions().put(parameter.getCardHandle(), session);

		// Create and return a StartCardSessionResponse
		StartCardSessionResponse response = new StartCardSessionResponse();
		response.setSessionId(sessionId);
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
		store.getCardSessions().remove(parameter.getSessionId());
		// Create and return a StopCardSessionResponse
		StopCardSessionResponse response = new StopCardSessionResponse();
		response.setStatus(new de.gematik.ws.conn.connectorcommon.v5.Status());
		response.getStatus().setResult("OK");
		return response;
	}

	@Override
	public PinResponseType verifyPin(VerifyPin parameter) throws FaultMessage {
		return null;
	}
}
