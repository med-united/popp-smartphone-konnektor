package de.servicehealth.popp.session;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import java.security.cert.X509Certificate;

public record EgkSession(
    String cardSessionId,
    CardInfoType cardInfoType,
    X509Certificate x509AuthECC,
    ApduScenario apduScenario,
    boolean sessionStarted) {}
