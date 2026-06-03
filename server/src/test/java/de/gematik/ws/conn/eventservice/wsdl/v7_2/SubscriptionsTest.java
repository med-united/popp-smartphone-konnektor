package de.gematik.ws.conn.eventservice.wsdl.v7_2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.ws.conn.eventservice.v7.SubscriptionType;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionsTest {

  private Subscriptions subscriptions;

  @BeforeEach
  public void init() {
    subscriptions = new Subscriptions(null);
  }

  @Test
  void addSubscription() {
    var subscriptionType = mock(SubscriptionType.class);
    when(subscriptionType.getSubscriptionID()).thenReturn("1");
    subscriptions.addSubscription("a", subscriptionType);

    var retrievedA =
        subscriptions.findSubscription(
            "a", (type) -> Objects.equals(type.getSubscriptionID(), "1"));
    var retrievedAWrongId =
        subscriptions.findSubscription(
            "a", (type) -> Objects.equals(type.getSubscriptionID(), "2"));
    var retrievedB =
        subscriptions.findSubscription(
            "b", (type) -> Objects.equals(type.getSubscriptionID(), "1"));

    assertTrue(retrievedA.isPresent());
    assertFalse(retrievedAWrongId.isPresent());
    assertFalse(retrievedB.isPresent());
  }

  @Test
  void getSubscriptions() {
    var subscriptionTypeA1 = mock(SubscriptionType.class);
    var subscriptionTypeA2 = mock(SubscriptionType.class);

    var subscriptionTypeB = mock(SubscriptionType.class);

    subscriptions.addSubscription("a", subscriptionTypeA1);
    subscriptions.addSubscription("a", subscriptionTypeA2);
    subscriptions.addSubscription("b", subscriptionTypeB);

    var retrievedA = subscriptions.getSubscriptions("a");
    var retrievedB = subscriptions.getSubscriptions("b");
    var retrievedC = subscriptions.getSubscriptions("c");

    assertEquals(2, retrievedA.size());
    assertEquals(1, retrievedB.size());
    assertEquals(0, retrievedC.size());

    assertEquals(subscriptionTypeA1, retrievedA.getFirst());
    assertEquals(subscriptionTypeA2, retrievedA.get(1));
    assertEquals(subscriptionTypeB, retrievedB.getFirst());

    // Test immutability of internal lists
    assertThrows(UnsupportedOperationException.class, () -> retrievedC.add(subscriptionTypeA1));
  }

  @Test
  void removeSubscription() {
    var subscriptionType = mock(SubscriptionType.class);
    when(subscriptionType.getSubscriptionID()).thenReturn("1");

    subscriptions.addSubscription("a", subscriptionType);
    var retrieved = subscriptions.getSubscriptions("a");

    assertEquals(1, retrieved.size());
    assertEquals(subscriptionType, retrieved.getFirst());

    subscriptions.removeSubscription("b", type -> type.getSubscriptionID().equals("1"));
    retrieved = subscriptions.getSubscriptions("a");

    assertEquals(1, retrieved.size());
    assertEquals(subscriptionType, retrieved.getFirst());

    subscriptions.removeSubscription("a", type -> type.getSubscriptionID().equals("2"));
    retrieved = subscriptions.getSubscriptions("a");

    assertEquals(1, retrieved.size());
    assertEquals(subscriptionType, retrieved.getFirst());

    subscriptions.removeSubscription("a", type -> type.getSubscriptionID().equals("1"));
    retrieved = subscriptions.getSubscriptions("a");

    assertTrue(retrieved.isEmpty());
  }

  @Test
  void subscriptionCount() {
    assertEquals(0, subscriptions.subscriptionCount());

    var subscriptionType1 = mock(SubscriptionType.class);
    when(subscriptionType1.getSubscriptionID()).thenReturn("1");

    subscriptions.addSubscription("a", subscriptionType1);
    assertEquals(1, subscriptions.subscriptionCount());

    subscriptions.removeSubscription("a", type -> type.getSubscriptionID().equals("1"));
    assertEquals(0, subscriptions.subscriptionCount());
  }
}
