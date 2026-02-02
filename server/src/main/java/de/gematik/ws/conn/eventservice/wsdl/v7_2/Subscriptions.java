package de.gematik.ws.conn.eventservice.wsdl.v7_2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.gematik.ws.conn.eventservice.v7.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Subscriptions {
    
    Map<String, List<SubscriptionType>> tlsCertCN2subscriptions = new ConcurrentHashMap<>();

    public Map<String, List<SubscriptionType>> getTlsCertCN2subscriptions() {
        return tlsCertCN2subscriptions;
    }

    public void setTlsCertCN2subscriptions(Map<String, List<SubscriptionType>> tlsCertCN2subscriptions) {
        this.tlsCertCN2subscriptions = tlsCertCN2subscriptions;
    }

}
