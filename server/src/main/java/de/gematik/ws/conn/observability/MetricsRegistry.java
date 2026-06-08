package de.gematik.ws.conn.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
@Startup
public class MetricsRegistry {

  private final MeterRegistry meterRegistry;

  public enum Counters {
    WEBSOCKETS_OPENED("websocket_opened"),
    WEBSOCKETS_CLOSED("websocket_closed"),
    CARDS_REGISTERED("egk_registered"),
    PRESCRIPTIONS_RETURNED("prescriptions_returned");

    private final String name;
    private Counter counter;

    Counters(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    private void setCounter(Counter counter) {
      this.counter = counter;
    }

    public void increment() {
      if (Objects.isNull(counter)) {
        return;
      }
      counter.increment();
    }
  }

  @PostConstruct
  private void init() {
    for (Counters counter : Counters.values()) {
      var micrometerCounter = Counter.builder(counter.getName()).register(meterRegistry);
      counter.setCounter(micrometerCounter);
    }
  }

  @Inject
  public MetricsRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }
}
