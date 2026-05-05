package de.servicehealth.servicediscovery;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.microprofile.config.Config;

@Path("/connector.sds")
public class ServiceDiscoveryController {

  @Inject
  Config config;

  record ReplacementConfig(String signature, String suffix) {

  }

  enum LocationReplacement {
    REAL_CONNECTOR(
        "popp.real-connector-url",
        List.of(new ReplacementConfig("$$AUTH_SIGNATURE_SERVICE_LOCATION$$", "/services/AuthSignatureService"))),
    SMARTPHONE_CONNECTOR(
        "popp.smartphone-connector-url",
        List.of(
            new ReplacementConfig("$$CARD_SERVICE_LOCATION$$", "/services/CardService"),
            new ReplacementConfig("$$EVENT_SERVICE_LOCATION$$", "/services/EventService")));

    private final String configParam;
    private final List<ReplacementConfig> replacementTargets;

    LocationReplacement(String configParam, List<ReplacementConfig> replacementTargets) {
      this.configParam = configParam;
      this.replacementTargets = replacementTargets;
    }

    public String getConfigParam() {
      return configParam;
    }

    public List<ReplacementConfig> getReplacementTargets() {
      return replacementTargets;
    }
  }

  private String serviceDiscoversWithReplacedTargets;

  @PostConstruct
  public void init() {
    try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("connector.sds")) {
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
    return serviceDiscoversWithReplacedTargets;
  }

  private String replaceLocations(String originalDocument) {
    String newDocument = originalDocument;
    for (LocationReplacement locationReplacement : LocationReplacement.values()) {
      String locationValue = config.getValue(locationReplacement.getConfigParam(), String.class);
      for (ReplacementConfig replacementTarget : locationReplacement.getReplacementTargets()) {
        newDocument = newDocument.replace(replacementTarget.signature(), locationValue + replacementTarget.suffix());
      }
    }
    return newDocument;
  }
}
