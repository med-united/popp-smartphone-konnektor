package de.servicehealth.servicediscovery;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/connector.sds")
public class ServiceDiscoveryController {

  @ConfigProperty(name = "popp.smartphone-connector-url")
  String smartphoneConnectorUrl;

  enum LocationReplacement {
    AUTH_SERVICE("$$AUTH_SIGNATURE_SERVICE_LOCATION$$", "/konnektor-proxy/AuthSignatureService"),
    CERT_SERVICE("$$CERTIFICATE_SERVICE_LOCATION$$", "/konnektor-proxy/CertificateService"),
    CARD_SERVICE("$$CARD_SERVICE_LOCATION$$", "/services/CardService"),
    EVENT_SERVICE("$$EVENT_SERVICE_LOCATION$$", "/services/EventService"),
    VSD_SERVICE("$$VSD_SERVICE_LOCATION$$", "/services/VSDService");

    private final String signature;
    private final String suffix;

    LocationReplacement(String signature, String suffix) {
      this.signature = signature;
      this.suffix = suffix;
    }

    public String getSignature() {
      return signature;
    }

    public String getSuffix() {
      return suffix;
    }
  }

  private String serviceDiscoversWithReplacedTargets;

  @PostConstruct
  public void init() {
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("connector.sds")) {
      if (input == null) {
        throw new IllegalStateException("Resource not found: connector.sds");
      }
      serviceDiscoversWithReplacedTargets = replaceLocations(new String(input.readAllBytes()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_XML)
  public String getServiceDiscoverDocument() {
    System.out.println("GETTING SDS");
    return serviceDiscoversWithReplacedTargets;
  }

  private String replaceLocations(String originalDocument) {
    String newDocument = originalDocument;
    for (LocationReplacement locationReplacement : LocationReplacement.values()) {
      newDocument =
          newDocument.replace(
              locationReplacement.getSignature(),
              smartphoneConnectorUrl + locationReplacement.getSuffix());
    }
    return newDocument;
  }
}
