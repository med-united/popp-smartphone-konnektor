package de.servicehealth.cetp;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.eventservice.v7.Event;
import de.gematik.ws.conn.eventservice.v7.Event.Message.Parameter;
import de.gematik.ws.conn.eventservice.v7.EventSeverityType;
import de.gematik.ws.conn.eventservice.v7.EventType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.Subscriptions;
import de.servicehealth.event.CardInserted;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;

@ApplicationScoped
public class CETPSender {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(CETPSender.class.getName());

    @Inject
    Subscriptions subscriptions;

    JAXBContext jaxbContext;

    public CETPSender() {
        try {
            jaxbContext = JAXBContext.newInstance(Event.class);
        } catch (Exception e) {
            LOG.severe("Failed to create JAXBContext: " + e.getMessage());
        }
    }

    public void onCardInfoTypeEvent(@Observes CardInserted cardInserted) {
        // Handle the CardInfoType event
        // For example, send it to an external system or log it
        LOG.info("Received CardInfoType event: " + cardInserted);

        // You can access the Store if needed
        String tlsCertCN = cardInserted.getTlsCertCN();
        CardInfoType cardInfoType = cardInserted.getCardType();

        if(subscriptions.getTlsCertCN2subscriptions().containsKey(tlsCertCN)) {
            LOG.info("There are subscriptions for tlsCertCN: " + tlsCertCN);
            // Further processing based on subscriptions
            for(var subscription : subscriptions.getTlsCertCN2subscriptions().get(tlsCertCN)) {
                LOG.info("Processing subscription: " + subscription);
                /*
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Event xmlns="http://ws.gematik.de/conn/EventService/v7.2">
                <Topic>CARD/INSERTED</Topic>
                <Type>Operation</Type>
                <Severity>Info</Severity>
                <SubscriptionID>6404443e-eb13-49d1-93a7-ec78f75573bb</SubscriptionID>
                <Message>
                    <Parameter>
                        <Key>CardHandle</Key>
                        <Value>c2006cfb-876b-4f51-8aed-6db04e03732c</Value>
                    </Parameter>
                    <Parameter>
                        <Key>CardType</Key>
                        <Value>EGK</Value>
                    </Parameter>
                    <Parameter>
                        <Key>ICCSN</Key>
                        <Value>80276881029979541601</Value>
                    </Parameter>
                    <Parameter>
                        <Key>CtID</Key>
                        <Value>00:1B:B5:08:90:41</Value>
                    </Parameter>
                    <Parameter>
                        <Key>SlotID</Key>
                        <Value>1</Value>
                    </Parameter>
                    <Parameter>
                        <Key>InsertTime</Key>
                        <Value>2024-02-24T21:34:13.734Z</Value>
                    </Parameter>
                    <Parameter>
                        <Key>CardHolderName</Key>
                        <Value>Bernd Blau</Value>
                    </Parameter>
                    <Parameter>
                        <Key>CertExpirationDate</Key>
                        <Value>2028-02-21T13:11:56Z</Value>
                    </Parameter>
                    <Parameter>
                        <Key>CardVersion</Key>
                        <Value>COSVERSION 4.4.1, OBJECTSYSTEMVERSION 4.5.1</Value>
                    </Parameter>
                    <Parameter>
                        <Key>KVNR</Key>
                        <Value>K210140155</Value>
                    </Parameter>
                </Message>
                </Event>
                */
                // Implement your logic here
                Event event = new Event();
                event.setTopic("CARD/INSERTED");
                event.setType(EventType.OPERATION);
                event.setSeverity(EventSeverityType.INFO);
                event.setSubscriptionID(subscription.getSubscriptionID());
                Event.Message message = new Event.Message();
                message.getParameter().add(createParameter("CardHandle", cardInfoType.getCardHandle()));
                message.getParameter().add(createParameter("CardType", cardInfoType.getCardType().value()));
                message.getParameter().add(createParameter("ICCSN", cardInfoType.getIccsn()));
                message.getParameter().add(createParameter("CtID", null));
                message.getParameter().add(createParameter("SlotID", null));
                message.getParameter().add(createParameter("InsertTime", cardInfoType.getInsertTime().toString()));
                message.getParameter().add(createParameter("CardHolderName", cardInfoType.getCardHolderName()));
                message.getParameter().add(createParameter("CertExpirationDate", null));
                message.getParameter().add(createParameter("CardVersion", cardInfoType.getCardVersion().toString()));
                message.getParameter().add(createParameter("KVNR", cardInfoType.getKvnr()));
                event.setMessage(message);
                
                try {
                    // build tls socket to send event to subscription endpoint
                    String eventTo = subscription.getEventTo();
                    URI uri = new URI(eventTo);
                    String host = uri.getHost();
                    int port = uri.getPort() != -1 ? uri.getPort() : 8444;
                    LOG.info("Sending event to " + host + ":" + port);

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    jaxbContext.createMarshaller().marshal(event, outputStream);
                    byte[] eventBytes = outputStream.toByteArray();
                    LOG.fine("Marshalled event: " + new String(eventBytes));

                    // Send eventBytes over SSL socket with length prefix
                    javax.net.ssl.SSLSocketFactory factory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                    try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) factory.createSocket(host, port);
                         java.io.OutputStream out = socket.getOutputStream()) {
                        out.write(new byte[] {'C', 'E', 'T', 'P'}); // CETP magic bytes
                        // Write length (4 bytes, big-endian)
                        int len = eventBytes.length;
                        out.write(new byte[] {
                            (byte)((len >> 24) & 0xFF),
                            (byte)((len >> 16) & 0xFF),
                            (byte)((len >> 8) & 0xFF),
                            (byte)(len & 0xFF)
                        });
                        // Write eventBytes
                        out.write(eventBytes);
                        out.flush();
                        LOG.info("Sent eventBytes of length " + len + " to " + host + ":" + port);
                    } catch (Exception e) {
                        LOG.severe("Failed to send eventBytes: " + e.getMessage());
                    }

                } catch (jakarta.xml.bind.JAXBException | URISyntaxException e) {
                    LOG.severe("Failed to marshal event: " + e.getMessage());
                }

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
