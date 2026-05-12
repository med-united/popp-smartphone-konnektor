package de.servicehealth.popp.session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApduScenario {

  private final AtomicBoolean isInitialized = new AtomicBoolean(false);

  private Map<String, ApduScenarioEntry> futureEntries =
      Collections.synchronizedMap(new LinkedHashMap<>());

  public void initializeScenario(List<String> apdus) {
    var isInitialized = this.isInitialized.compareAndExchange(false, true);

    if (isInitialized) {
      throw new RuntimeException("Trying to initialize an already initialized APDUScenario");
    }

    for (String apdu : apdus) {
      var correlationId = UUID.randomUUID().toString();
      futureEntries.put(
          correlationId, new ApduScenarioEntry(correlationId, apdu, new CompletableFuture<>()));
    }
  }

  void clear() {
    futureEntries = Collections.synchronizedMap(new LinkedHashMap<>());
    isInitialized.set(false);
  }

  public synchronized void completeApdu(String correlationId, String value) {
    CompletableFuture<String> future =
        Optional.ofNullable(futureEntries.get(correlationId))
            .map(ApduScenarioEntry::future)
            .orElseThrow();

    if (future.isDone()) {
      throw new RuntimeException("Trying to complete an already completed future");
    }

    future.complete(value);
  }

  public List<ApduScenarioEntry> getApduScenarioEntries() {
    return List.copyOf(futureEntries.values());
  }

  public Future<List<String>> getApduResults() {
    return CompletableFuture.allOf(
            futureEntries.values().stream()
                .map(ApduScenarioEntry::future)
                .toArray(CompletableFuture[]::new))
        .thenApply(
            (v) ->
                futureEntries.values().stream()
                    .map(ApduScenarioEntry::future)
                    .map(CompletableFuture::join)
                    .toList());
  }
}
