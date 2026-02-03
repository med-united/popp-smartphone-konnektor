package de.servicehealth.popp.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.servicehealth.cardlink.model.RegisterEgkPayload;
import de.servicehealth.event.CardInserted;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.util.concurrent.CopyOnWriteArrayList;


@ApplicationScoped
public class Store {
    private Map<String, List<Entry>> tlsCertCNs2cards = new HashMap<>();
    private List<String> tlsCertCNs = new ArrayList<>();
    private Map<String, Session> cardSessions = new HashMap<>();

    @Inject
    Event<CardInserted> cardInsertedEvent;

    public Map<String, List<Entry>> getTlsCertCNs2cards() {
        return tlsCertCNs2cards;
    }

    public void setTlsCertCNs2cards(Map<String, List<Entry>> tlsCertCNs2cards) {
        this.tlsCertCNs2cards = tlsCertCNs2cards;
    }

    public List<String> getTlsCertCNs() {
        return tlsCertCNs;
    }

    public void setTlsCertCNs(List<String> tlsCertCNs) {
        this.tlsCertCNs = tlsCertCNs;
    }

    public void addEntry(Entry entry) {
        this.tlsCertCNs2cards.computeIfAbsent(entry.getTlsCertCN(), k -> new ArrayList<>()).add(entry);
    }

    public void removeEntry(String tlsCertCN, Session session) {
        this.tlsCertCNs2cards.get(tlsCertCN).removeIf(entry -> entry.getSession().equals(session));
    }

    public Map<String, Session> getCardSessions() {
        return cardSessions;
    }

    public void setCardSessions(Map<String, Session> cardSessions) {
        this.cardSessions = cardSessions;
    }

    public Session getSessionForCardHandle(String tlsCertCN, String cardHandle) {
        var list = this.tlsCertCNs2cards.get(tlsCertCN);

        if(list == null) {
            list = new CopyOnWriteArrayList<>();
            this.tlsCertCNs2cards.put(tlsCertCN, list);
        }
        
        return list.stream()
            .filter(entry -> entry.getCardInfoType().getCardHandle().equals(cardHandle))
            .findFirst()
            .map(Entry::getSession)
            .orElse(null);
    }

    public void registerEGK(String tlsCertCN, Session session, RegisterEgkPayload egkPayload, String cardSessionId) {
        Optional<Entry> optionalSession = this.tlsCertCNs2cards.get(tlsCertCN).stream()
            .filter(entry -> entry.getSession().equals(session))
            .findFirst();
        if (!optionalSession.isPresent()) {
            // Log warning: session not found
            Log.warn("Session not found.");
            return;
        } else {
            Entry entry = optionalSession.get();
            entry.setCardSessionId(cardSessionId);
            entry.setRegisterEgkPayload(egkPayload);
            if(cardInsertedEvent != null) {
                cardInsertedEvent.fire(new CardInserted(tlsCertCN, entry.getCardInfoType()));
            }
        }
    }
}
