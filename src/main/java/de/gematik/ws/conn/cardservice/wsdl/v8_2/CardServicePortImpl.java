package de.gematik.ws.conn.cardservice.wsdl.v8_2;

import de.gematik.ws.conn.cardservice.v8.*;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.servicehealth.popp.session.Store;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;

@jakarta.jws.WebService(serviceName = "CardService", portName = "CardServicePortType", targetNamespace = "http://ws.gematik.de/conn/CardService/WSDL/v8.2", wsdlLocation = "classpath:/wsdl/CardService_v8_2_1.wsdl", endpointInterface = "de.gematik.ws.conn.cardservice.wsdl.v8_2.CardServicePortType")
public class CardServicePortImpl implements CardServicePortType {
	@Inject
	Store store;

	@Inject
    SecurityIdentity identity; // Inject the SecurityIdentity

	private static final Logger LOG = Logger.getLogger(CardServicePortImpl.class.getName());

	@Override
	public SecureSendAPDUResponse secureSendAPDU(SecureSendAPDU parameter) throws FaultMessage {
		LOG.log(Level.FINE, "Executing secureSendAPDU");
		byte[] gzipBytes = new byte[0];
		// Länge (2 Bytes) + GZIP-Daten zusammenführen
		ByteBuffer buffer = ByteBuffer.allocate(2 + gzipBytes.length);
		buffer.putShort((short) gzipBytes.length);
		buffer.put(gzipBytes);
		SecureSendAPDUResponse response = new SecureSendAPDUResponse();
		SignedScenarioResponseType responseType = new SignedScenarioResponseType();
		responseType.setResponseApduList(new SignedScenarioResponseType.ResponseApduList());
		responseType.getResponseApduList().getResponseApdu().add(HexFormat.of().formatHex(buffer.array()));
		response.setSignedScenarioResponse(responseType);
		return response;
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
		response.setStatus(new de.gematik.ws.conn.connectorcommon.v5.Status());
		response.getStatus().setResult("OK");
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
