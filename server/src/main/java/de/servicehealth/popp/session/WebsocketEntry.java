package de.servicehealth.popp.session;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import io.quarkus.logging.Log;
import jakarta.json.JsonObject;
import jakarta.websocket.Session;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

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
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
      x509AuthECC = (X509Certificate) cf.generateCertificate(bais);
      cardInfoType =
          createCardTypeFromx509AuthECC(registerEgkPayload.getString("cardSessionId"), x509AuthECC);
    } catch (Exception e) {
      Log.debug("X509 Certificate Creation failed: " + e.getMessage());
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

    CardInfoType cardInfo = new CardInfoType();
    cardInfo.setCardHandle(cardSessionId2);
    cardInfo.setCtId(cardSessionId2);
    cardInfo.setCardType(CardTypeType.EGK);
    cardInfo.setIccsn(x509AuthECC2.getSerialNumber().toString());

    // Robust parsing of DN using LDAP library
    String kvnr = null;
    try {
      LdapName ldapDN = new LdapName(x509AuthECC2.getSubjectX500Principal().getName());
      cardInfo.setCardHolderName(
          ldapDN.getRdns().stream()
              .filter(rdn -> "CN".equalsIgnoreCase(rdn.getType()))
              .map(rdn -> rdn.getValue().toString())
              .findFirst()
              .orElse(null));
      int ouCount = 0;
      for (Rdn rdn : ldapDN.getRdns()) {
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
      Log.error(e.getMessage());
    }
    cardInfo.setInsertTime(nowAsXMLGregorianCalendar());
    // Add more fields as needed based on CardInfoType definition
    return cardInfo;
  }

  /** Gibt das aktuelle Datum/Zeit als XMLGregorianCalendar zurück. */
  public static XMLGregorianCalendar nowAsXMLGregorianCalendar() {
    try {
      DatatypeFactory df = DatatypeFactory.newInstance();
      GregorianCalendar gc = new GregorianCalendar();
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
