package de.servicehealth.cetp;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.eventservice.v7.Event;
import de.gematik.ws.conn.eventservice.v7.Event.Message.Parameter;
import de.gematik.ws.conn.eventservice.v7.EventSeverityType;
import de.gematik.ws.conn.eventservice.v7.EventType;
import de.gematik.ws.conn.eventservice.v7.SubscriptionType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.Subscriptions;
import de.servicehealth.event.CardInserted;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@ApplicationScoped
public class CETPClient {

  private static final Logger LOG = Logger.getLogger(CETPClient.class.getName());

  @Inject Subscriptions subscriptions;

  JAXBContext jaxbContext;

  public CETPClient() {
    try {
      jaxbContext = JAXBContext.newInstance(Event.class);
    } catch (Exception e) {
      LOG.severe("Failed to create JAXBContext: " + e.getMessage());
    }
  }

  public void onCardInfoTypeEvent(@Observes CardInserted cardInserted) {
    // Handle the CardInfoType event
    // For example, send it to an external system or log it
    LOG.fine("Received CardInfoType event: " + cardInserted);

    // You can access the Store if needed
    String tlsCertCN = cardInserted.getTlsCertCN();
    CardInfoType cardInfoType = cardInserted.getCardType();

    List<SubscriptionType> subscriptionOfCN = subscriptions.getSubscriptions(tlsCertCN);

    if (subscriptionOfCN.isEmpty()) {
      LOG.warning("There are no subscriptions for tlsCertCN: " + tlsCertCN);
      return;
    }

    // Further processing based on subscriptions
    for (var subscription : subscriptionOfCN) {
      LOG.fine("Processing subscription: " + subscription);
      Event event = new Event();
      event.setTopic("CARD/INSERTED");
      event.setType(EventType.OPERATION);
      event.setSeverity(EventSeverityType.INFO);
      event.setSubscriptionID(subscription.getSubscriptionID());
      Event.Message message = new Event.Message();
      message.getParameter().add(createParameter("CardHandle", cardInfoType.getCardHandle()));
      message.getParameter().add(createParameter("CardType", cardInfoType.getCardType().value()));
      message.getParameter().add(createParameter("ICCSN", cardInfoType.getIccsn()));
      // UHP: Has to be non null
      message.getParameter().add(createParameter("CtID", cardInfoType.getCtId()));
      // UHP: Has to be non null
      message.getParameter().add(createParameter("SlotID", "1"));
      message
          .getParameter()
          .add(createParameter("InsertTime", cardInfoType.getInsertTime().toString()));
      message
          .getParameter()
          .add(createParameter("CardHolderName", cardInfoType.getCardHolderName()));
      // UHP: Has to be non null
      message.getParameter().add(createParameter("CertExpirationDate", ""));
      // UHP: Has to be non null
      message.getParameter().add(createParameter("CardVersion", ""));
      message.getParameter().add(createParameter("KVNR", cardInfoType.getKvnr()));
      event.setMessage(message);

      try {

        // build tls socket to send event to subscription endpoint
        String eventTo = subscription.getEventTo();
        URI uri = new URI(eventTo);
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : 8444;
        LOG.fine("Sending event to " + host + ":" + port);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        jaxbContext.createMarshaller().marshal(event, outputStream);
        byte[] eventBytes = outputStream.toByteArray();

        TrustManager[] trustAllCerts =
            new TrustManager[] {
              new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                  return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
              }
            };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        SSLSocketFactory factory = sslContext.getSocketFactory();

        // Send eventBytes over SSL socket with length prefix
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            OutputStream out = socket.getOutputStream()) {
          out.write(new byte[] {'C', 'E', 'T', 'P'}); // CETP magic bytes
          // Write length (4 bytes, big-endian)
          int len = eventBytes.length;
          out.write(
              new byte[] {
                (byte) ((len >> 24) & 0xFF),
                (byte) ((len >> 16) & 0xFF),
                (byte) ((len >> 8) & 0xFF),
                (byte) (len & 0xFF)
              });
          // Write eventBytes
          out.write(eventBytes);
          out.flush();
          LOG.fine("Sent eventBytes of length " + len + " to " + host + ":" + port);
        } catch (Exception e) {
          LOG.severe("Failed to send eventBytes: " + e.getMessage());
        }

      } catch (JAXBException
          | URISyntaxException
          | NoSuchAlgorithmException
          | KeyManagementException e) {
        LOG.severe("Failed to marshal event: " + e.getMessage());
      }
    }
  }

  private Parameter createParameter(String key, String value) {
    Parameter parameter = new Parameter();
    parameter.setKey(key);
    parameter.setValue(value);
    return parameter;
  }
}
