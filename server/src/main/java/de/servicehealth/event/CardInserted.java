package de.servicehealth.event;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;

public class CardInserted {
    private String tlsCertCN;
    private CardInfoType cardType;

    public CardInserted() {
    }

    public CardInserted(String tlsCertCN, CardInfoType cardType) {
        this.tlsCertCN = tlsCertCN;
        this.cardType = cardType;
    }

    public String getTlsCertCN() {
        return tlsCertCN;
    }

    public void setTlsCertCN(String tlsCertCN) {
        this.tlsCertCN = tlsCertCN;
    }

    public CardInfoType getCardType() {
        return cardType;
    }

    public void setCardType(CardInfoType cardType) {
        this.cardType = cardType;
    }

}
