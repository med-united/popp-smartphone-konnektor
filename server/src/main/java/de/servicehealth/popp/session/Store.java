package de.servicehealth.popp.session;

import de.servicehealth.event.CardInserted;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.websocket.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

@ApplicationScoped
public class Store {
  private final Map<String, List<WebsocketEntry>> tlsCertCNs2cards = new ConcurrentHashMap<>();

  @Inject Event<CardInserted> cardInsertedEvent;

  public List<WebsocketEntry> getEntriesOfCertCN(String certCn) {
    return tlsCertCNs2cards.get(certCn).stream().filter(WebsocketEntry::isEgkRegistered).toList();
  }

  public void addEntry(WebsocketEntry entry) {
    tlsCertCNs2cards
        .computeIfAbsent(entry.getTlsCertCN(), k -> new CopyOnWriteArrayList<>())
        .add(entry);
  }

  public void removeEntryBySessionId(String sessionId) {
    tlsCertCNs2cards
        .values()
        .forEach(
            list -> {
              list.removeIf(entry -> Objects.equals(entry.getSession().getId(), sessionId));
            });
  }

  public Optional<WebsocketEntry> findEntry(String tlsCertCN, String cardSessionId) {
    return this.tlsCertCNs2cards.get(tlsCertCN).stream()
        .filter(
            entry ->
                entry.getCardSessionId() != null && entry.getCardSessionId().equals(cardSessionId))
        .findFirst();
  }

  public Session getSessionForCardHandle(String tlsCertCN, String cardHandle) {
    return tlsCertCNs2cards.getOrDefault(tlsCertCN, Collections.emptyList()).stream()
        .filter(
            entry ->
                entry.isEgkRegistered()
                    && entry.getCardInfoType().getCardHandle().equals(cardHandle))
        .findFirst()
        .map(WebsocketEntry::getSession)
        .orElse(null);
  }

  public void registerEGK(
      String tlsCertCN, Session session, JsonObject egkPayload, String cardSessionId) {

    Optional<WebsocketEntry> optionalSession =
        this.tlsCertCNs2cards.get(tlsCertCN).stream()
            .filter(entry -> entry.getSession().equals(session))
            .findFirst();

    if (optionalSession.isEmpty()) {
      Log.warn("Session not found.");
    } else {
      WebsocketEntry entry = optionalSession.get();
      entry.registerCard(cardSessionId, egkPayload);
      cardInsertedEvent.fire(new CardInserted(tlsCertCN, entry.getCardInfoType()));
    }
  }

  public Future<List<String>> getAPDUResponses(String tlsCertCN, String cardSessionId) {
    Optional<WebsocketEntry> optionalSession = findEntry(tlsCertCN, cardSessionId);
    if (optionalSession.isEmpty()) {
      // Log warning: session not found
      Log.warn("Session not found for APDU responses.");
      return null;
    } else {
      WebsocketEntry entry = optionalSession.get();
      return entry.getApduResponses();
    }
  }
}
