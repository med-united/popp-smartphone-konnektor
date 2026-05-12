package de.servicehealth.popp.cardlink;

import de.servicehealth.popp.session.ApduScenarioInitilizedEvent;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@ApplicationScoped
public class ApduDispatcherService {
  private static final Logger LOG = Logger.getLogger(ApduDispatcherService.class.getName());

  void dispatchApduScenario(@Observes ApduScenarioInitilizedEvent event) {
    var entry = event.entry();
    try {
      for (var scenarioEntry : event.entry().getApduScenarioEntries()) {
        JsonArray sendApduPayload =
            Json.createArrayBuilder()
                .add(
                    Json.createObjectBuilder()
                        .add("type", "sendAPDU")
                        .add("payload", scenarioEntry.payload()))
                .add(entry.getCardSessionId())
                .add(scenarioEntry.correlationId())
                .build();
        entry.sendText(sendApduPayload.toString());
      }
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to send APDU command over WebSocket", e);
    }
  }
}
