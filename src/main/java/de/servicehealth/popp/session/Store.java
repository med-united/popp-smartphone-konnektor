package de.servicehealth.popp.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;


@ApplicationScoped
public class Store {
    private Map<String, List<Entry>> tlsCertCNs2cards = new HashMap<>();
    private List<String> tlsCertCNs = new ArrayList<>();
    private Map<String, Session> cardSessions = new HashMap<>();

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
        return this.tlsCertCNs2cards.get(tlsCertCN).stream()
            .filter(entry -> entry.getCardInfoType().getCardHandle().equals(cardHandle))
            .findFirst()
            .map(Entry::getSession)
            .orElse(null);
    }
}
