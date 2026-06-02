package de.gematik.ws.conn.eventservice.wsdl.v7_2;

import de.gematik.ws.conn.eventservice.v7.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@ApplicationScoped
public class Subscriptions {

  private final Map<String, List<SubscriptionType>> tlsCertCN2subscriptions =
      new ConcurrentHashMap<>();

  public synchronized void addSubscription(String tlsCertCn, SubscriptionType type) {
    var subscriptionsList =
        tlsCertCN2subscriptions.computeIfAbsent(tlsCertCn, (key) -> new CopyOnWriteArrayList<>());
    subscriptionsList.add(type);
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
