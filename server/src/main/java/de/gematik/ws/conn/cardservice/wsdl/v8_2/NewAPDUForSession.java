package de.gematik.ws.conn.cardservice.wsdl.v8_2;

import jakarta.websocket.Session;

public class NewAPDUForSession {
    public Session session;
    public String cardSessionId;
    public String tlsCertCN;

    public NewAPDUForSession(Session session, String cardSessionId, String tlsCertCN) {
        this.session = session;
        this.cardSessionId = cardSessionId;
        this.tlsCertCN = tlsCertCN;
    }

}
