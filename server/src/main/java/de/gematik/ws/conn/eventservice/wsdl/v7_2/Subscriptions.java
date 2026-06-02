package de.gematik.ws.conn.eventservice.wsdl.v7_2;

import de.gematik.ws.conn.eventservice.v7.SubscriptionType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ApplicationScoped
public class Subscriptions {

  @Inject private MeterRegistry registry;

  @PostConstruct
  private void init() {

    Gauge.builder("jvm.threads.peak", this, Subscriptions::subscriptionCount)
        .description("The peak live thread count...") // optional
        .tags("key", "value") // optional
        .register(registry);
  }

  private final Map<String, List<SubscriptionType>> tlsCertCN2subscriptions =
      new ConcurrentHashMap<>();

  public void addSubscription(String tlsCertCn, SubscriptionType type) {
    tlsCertCN2subscriptions.computeIfAbsent(tlsCertCn, (key) -> new ArrayList<>());
    tlsCertCN2subscriptions.computeIfPresent(
        tlsCertCn,
        (key, val) -> {
          val.add(type);
          return val;
        });
  }

  public List<SubscriptionType> getSubscriptions(String tlsCertCn) {
    return List.copyOf(tlsCertCN2subscriptions.getOrDefault(tlsCertCn, Collections.emptyList()));
  }

  public Optional<SubscriptionType> findSubscription(
      String tlsCertCn, Predicate<SubscriptionType> predicate) {
    return tlsCertCN2subscriptions.getOrDefault(tlsCertCn, Collections.emptyList()).stream()
        .filter(predicate)
        .findFirst();
  }

  public void removeSubscription(String tlsCertCn, Predicate<SubscriptionType> predicate) {
    tlsCertCN2subscriptions.computeIfPresent(
        tlsCertCn,
        (key, list) -> {
          list.removeIf(predicate);
          return list;
        });
  }

  public int subscriptionCount() {
    return tlsCertCN2subscriptions.values().stream().map(List::size).reduce(Integer::sum).orElse(0);
  }
}
