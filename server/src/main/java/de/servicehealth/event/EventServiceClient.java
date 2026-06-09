package de.servicehealth.event;

import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.BindingProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EventServiceClient {
  @ConfigProperty(name = "popp.mandant-id")
  String mandantId;

  @ConfigProperty(name = "popp.workplace-id")
  String workplaceId;

  @ConfigProperty(name = "popp.client-system-id")
  String clientSystemId;

  private final EventServicePortType port;

  public EventServiceClient(@ConfigProperty(name = "popp.konnektor-http-port") int proxyPort) {
    String proxyUrl = "http://localhost:" + proxyPort + "/konnektor-proxy";
    System.out.println("PROXY_URL: " + proxyUrl);
    this.port = new EventService().getEventServicePort();
    ((BindingProvider) port)
        .getRequestContext()
        .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, proxyUrl);
  }

  public GetCardsResponse getCards() throws FaultMessage {
    ContextType context = new ContextType();
    context.setMandantId(mandantId);
    context.setClientSystemId(clientSystemId);
    context.setWorkplaceId(workplaceId);

    GetCards request = new GetCards();
    request.setContext(context);
    request.setCardType(CardTypeType.SMC_B);

    return port.getCards(request);
  }
}
