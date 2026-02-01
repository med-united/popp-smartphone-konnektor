package de.servicehealth.popp.session;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import jakarta.websocket.Session;

public class Entry {
    private String tlsCertCN;
    private CardInfoType cardInfoType;
    private Session session;

    public Entry(String tlsCertCN, Session session) {
        this.tlsCertCN = tlsCertCN;
        this.session = session;
    }

    public String getTlsCertCN() {
        return tlsCertCN;
    }

    public void setTlsCertCN(String tlsCertCN) {
        this.tlsCertCN = tlsCertCN;
    }

    public CardInfoType getCardInfoType() {
        return cardInfoType;
    }

    public void setCardInfoType(CardInfoType cardInfoType) {
        this.cardInfoType = cardInfoType;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }
}
