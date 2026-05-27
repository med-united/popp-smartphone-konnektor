package de.servicehealth.popp.session;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import jakarta.json.JsonObject;
import jakarta.websocket.Session;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

public class WebsocketEntry {
  private final String tlsCertCN;
  private final Session session;
  private EgkSession egkSession;

  public WebsocketEntry(String tlsCertCN, Session session) {
    this.tlsCertCN = tlsCertCN;
    this.session = session;
  }

  public String getCardSessionId() {
    return egkSession.cardSessionId();
  }

  synchronized void registerCard(String cardSessionId, JsonObject registerEgkPayload) {
    if (!Objects.isNull(egkSession)) {
      throw EgkException.CARD_SESSION_MODIFIED;
    }

    if (registerEgkPayload == null || registerEgkPayload.getString("x509AuthECC") == null) {
      throw new RuntimeException("Trying to insert Card without payload. This is not allowed");
    }

    X509Certificate x509AuthECC;
    CardInfoType cardInfoType;

    try {
      byte[] certBytes = Base64.getDecoder().decode(registerEgkPayload.getString("x509AuthECC"));
      CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
      ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
      x509AuthECC = (java.security.cert.X509Certificate) cf.generateCertificate(bais);
      cardInfoType =
          createCardTypeFromx509AuthECC(registerEgkPayload.getString("cardSessionId"), x509AuthECC);
    } catch (Exception e) {
      throw new RuntimeException("Can not generate x509AuthECC");
    }
    this.egkSession =
        new EgkSession(cardSessionId, cardInfoType, x509AuthECC, new ApduScenario(), false);
  }

  private CardInfoType createCardTypeFromx509AuthECC(
      String cardSessionId2, X509Certificate x509AuthECC2) {
    if (x509AuthECC2 == null) {
      throw new RuntimeException("Can not generate CardInfoType");
    }

    // echo
    // "MIIDbjCCAxSgAwIBAgIHAv8CpbCrEjAKBggqhkjOPQQDAjCBljELMAkGA1UEBhMCREUxHzAdBgNVBAoMFmdlbWF0aWsgR21iSCBOT1QtVkFMSUQxRTBDBgNVBAsMPEVsZWt0cm9uaXNjaGUgR2VzdW5kaGVpdHNrYXJ0ZS1DQSBkZXIgVGVsZW1hdGlraW5mcmFzdHJ1a3R1cjEfMB0GA1UEAwwWR0VNLkVHSy1DQTUxIFRFU1QtT05MWTAeFw0yNDA0MDIwMDAwMDBaFw0yOTA0MDEyMzU5NTlaMIHtMQswCQYDVQQGEwJERTEdMBsGA1UECgwUVGVzdCBHS1YtU1ZOT1QtVkFMSUQxEjAQBgNVBAsMCTEwOTUwMDk2OTETMBEGA1UECwwKWDExMDU2Nzk1NTETMBEGA1UEBAwKVGFuZ2Vyw7BhbDE8MDoGA1UEKgwzQW5uaWthIFZlcmEgTWVsaXNzYSBCcnVuaGlsZCBBcGhyb2RpdGUgRnJlaWZyYXUgdm9uMUMwQQYDVQQDDDpBbm5pa2EgVmVyYSBNZWxpc3NhIEIuIEEuIEZyZWlmcmF1IHZvbiBUYW5nZXLDsGFsVEVTVC1PTkxZMFowFAYHKoZIzj0CAQYJKyQDAwIIAQEHA0IABI1CINDvdCsVmzkRK7Uj/oulU/5CK/wWwI0oS6NIS2+9k/KFUKaVHQmmUKbDKWs5aMzlBcR/d0rnwIGauoLwZvijgfIwge8wOwYIKwYBBQUHAQEELzAtMCsGCCsGAQUFBzABhh9odHRwOi8vZWhjYS5nZW1hdGlrLmRlL2VjYy1vY3NwMCAGA1UdIAQZMBcwCgYIKoIUAEwEgSMwCQYHKoIUAEwERjAwBgUrJAgDAwQnMCUwIzAhMB8wHTAQDA5WZXJzaWNoZXJ0ZS8tcjAJBgcqghQATAQxMA4GA1UdDwEB/wQEAwIHgDAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFHTp+RSD4QvmEfYqrJfs2a+ZQ8HwMB0GA1UdDgQWBBRdbQO99+E/N/3QduuuQPwWMYyqfzAKBggqhkjOPQQDAgNIADBFAiEAqIrpdN2yN7G9zGzLV99zgopp69jbjsp+7hNSUkoPJhICIFWxIbPxNSv6ksUeRJ7sgdse1KDdyeFKhHQzmMyQtQ8Y" | base64 -d | openssl x509 -inform DER -noout -text
    // Certificate:
    // Data:
    //     Version: 3 (0x2)
    //     Serial Number: 843336788257554 (0x2ff02a5b0ab12)
    //     Signature Algorithm: ecdsa-with-SHA256
    //     Issuer: C=DE, O=gematik GmbH NOT-VALID, OU=Elektronische Gesundheitskarte-CA der
    // Telematikinfrastruktur, CN=GEM.EGK-CA51 TEST-ONLY
    //     Validity
    //         Not Before: Apr  2 00:00:00 2024 GMT
    //         Not After : Apr  1 23:59:59 2029 GMT
    //     Subject: C=DE, O=Test GKV-SVNOT-VALID, OU=109500969, OU=X110567955, SN=Tangerðal,
    // GN=Annika Vera Melissa Brunhild Aphrodite Freifrau von, CN=Annika Vera Melissa B. A. Freifrau
    // von TangerðalTEST-ONLY
    //
    //               Subject Public Key Info:
    //         Public Key Algorithm: id-ecPublicKey
    //             Public-Key: (256 bit)
    //             pub:
    //             04:8d:42:20:d0:ef:74:2b:15:9b:39:11:2b:b5:23:
    //             fe:8b:a5:53:fe:42:2b:fc:16:c0:8d:28:4b:a3:48:
    //             4b:6f:bd:93:f2:85:50:a6:95:1d:09:a6:50:a6:c3:
    //             29:6b:39:68:cc:e5:05:c4:7f:77:4a:e7:c0:81:9a:
    //             ba:82:f0:66:f8
    //         ASN1 OID: brainpoolP256r1
    // X509v3 extensions:
    //     Authority Information Access:
    //         OCSP - URI:http://ehca.gematik.de/ecc-ocsp
    //     X509v3 Certificate Policies:
    //         Policy: 1.2.276.0.76.4.163
    //         Policy: 1.2.276.0.76.4.70
    //     Professional Information or basis for Admission:
    //         Entry 1:
    //           Profession Info Entry 1:
    //             Info Entries:
    //               Versicherte/-r
    //             Profession OIDs:
    //               undefined (1.2.276.0.76.4.49)

    //     X509v3 Key Usage: critical
    //         Digital Signature
    //     X509v3 Basic Constraints: critical
    //         CA:FALSE
    //     X509v3 Authority Key Identifier:
    //         74:E9:F9:14:83:E1:0B:E6:11:F6:2A:AC:97:EC:D9:AF:99:43:C1:F0
    //     X509v3 Subject Key Identifier:
    //         5D:6D:03:BD:F7:E1:3F:37:FD:D0:76:EB:AE:40:FC:16:31:8C:AA:7F
    // Signature Algorithm: ecdsa-with-SHA256
    // Signature Value:
    //     30:45:02:21:00:a8:8a:e9:74:dd:b2:37:b1:bd:cc:6c:cb:57:
    //     df:73:82:8a:69:eb:d8:db:8e:ca:7e:ee:13:52:52:4a:0f:26:
    //     12:02:20:55:b1:21:b3:f1:35:2b:fa:92:c5:1e:44:9e:ec:81:
    //     db:1e:d4:a0:dd:c9:e1:4a:84:74:33:98:cc:90:b5:0f:18

    CardInfoType cardInfo = new CardInfoType();
    // Example: set subject DN and serial number from certificate
    cardInfo.setCardHandle(cardSessionId2);
    cardInfo.setCtId(cardSessionId2);
    cardInfo.setCardType(CardTypeType.EGK);
    cardInfo.setIccsn(x509AuthECC2.getSerialNumber().toString());
    // Robust parsing of DN using LDAP library
    String kvnr = null;
    try {
      javax.naming.ldap.LdapName ldapDN =
          new javax.naming.ldap.LdapName(x509AuthECC2.getSubjectX500Principal().getName());
      cardInfo.setCardHolderName(
          ldapDN.getRdns().stream()
              .filter(rdn -> "CN".equalsIgnoreCase(rdn.getType()))
              .map(rdn -> rdn.getValue().toString())
              .findFirst()
              .orElse(null));
      int ouCount = 0;
      for (javax.naming.ldap.Rdn rdn : ldapDN.getRdns()) {
        if ("OU".equalsIgnoreCase(rdn.getType())) {
          ouCount++;
          if (ouCount == 2) { // Second OU is KVNR
            kvnr = rdn.getValue().toString();
            break;
          }
        }
      }
      cardInfo.setKvnr(kvnr);
    } catch (Exception e) {
      e.printStackTrace();
    }
    cardInfo.setInsertTime(nowAsXMLGregorianCalendar());
    // Add more fields as needed based on CardInfoType definition
    return cardInfo;
  }

  /** Gibt das aktuelle Datum/Zeit als XMLGregorianCalendar zurück. */
  public static javax.xml.datatype.XMLGregorianCalendar nowAsXMLGregorianCalendar() {
    try {
      javax.xml.datatype.DatatypeFactory df = javax.xml.datatype.DatatypeFactory.newInstance();
      java.util.GregorianCalendar gc = new java.util.GregorianCalendar();
      gc.setTimeInMillis(System.currentTimeMillis());
      return df.newXMLGregorianCalendar(gc);
    } catch (Exception e) {
      return null;
    }
  }

  public X509Certificate getX509AuthECC() {
    return egkSession.x509AuthECC();
  }

  public String getTlsCertCN() {
    return tlsCertCN;
  }

  public void sendText(String text) {
    session.getAsyncRemote().sendText(text);
  }

  public boolean isEgkRegistered() {
    return !Objects.isNull(egkSession);
  }

  public CardInfoType getCardInfoType() {
    return egkSession.cardInfoType();
  }

  public Session getSession() {
    return session;
  }

  public void initializeApduScenario(List<String> payloads) {
    this.egkSession.apduScenario().initializeScenario(payloads);
  }

  public List<ApduScenarioEntry> getApduScenarioEntries() {
    return this.egkSession.apduScenario().getApduScenarioEntries();
  }

  public void completeApduResponse(String correlationId, String message) {
    this.egkSession.apduScenario().completeApdu(correlationId, message);
  }

  public Future<List<String>> getApduResponses() {
    return this.egkSession.apduScenario().getApduResults();
  }

  public synchronized void startApduSession() {
    if (this.egkSession.sessionStarted()) {
      throw new RuntimeException("Session has already been started");
    }
  }

  public void clearApduScenario() {
    this.egkSession.apduScenario().clear();
  }
}
