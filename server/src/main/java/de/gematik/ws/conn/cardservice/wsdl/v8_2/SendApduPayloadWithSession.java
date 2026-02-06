package de.gematik.ws.conn.cardservice.wsdl.v8_2;

import jakarta.websocket.Session;

public class SendApduPayloadWithSession {
    public de.servicehealth.cardlink.model.SendApduEnvelope envelope;
    public Session session;
    public String cardSessionId;
    public String tlsCertCN;
    public SendApduPayloadWithSession(de.servicehealth.cardlink.model.SendApduEnvelope envelope, Session session, String cardSessionId, String tlsCertCN) {
        this.envelope = envelope;
        this.session = session;
        this.cardSessionId = cardSessionId;
        this.tlsCertCN = tlsCertCN;
    }
}
