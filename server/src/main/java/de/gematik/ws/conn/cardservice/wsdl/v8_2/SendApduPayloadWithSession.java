package de.gematik.ws.conn.cardservice.wsdl.v8_2;

import de.servicehealth.cardlink.model.SendApduEnvelope;
import jakarta.websocket.Session;

public class SendApduPayloadWithSession {
  public SendApduEnvelope envelope;
  public Session session;
  public String cardSessionId;
  public String tlsCertCN;

  public SendApduPayloadWithSession(
      SendApduEnvelope envelope, Session session, String cardSessionId, String tlsCertCN) {
    this.envelope = envelope;
    this.session = session;
    this.cardSessionId = cardSessionId;
    this.tlsCertCN = tlsCertCN;
  }
}
