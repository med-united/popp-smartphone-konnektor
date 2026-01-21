package de.servicehealth.popp.cardlink;

import de.servicehealth.popp.session.Entry;
import de.servicehealth.popp.session.Store;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket/{tlsCertCN}")
public class Webservice {

    @Inject
    private Store store;

    @OnOpen
    public void onOpen(Session session, @PathParam("tlsCertCN") String tlsCertCN) {
        // Handle new connection
        System.out.println("WebSocket opened for tlsCertCN: " + tlsCertCN);
        // Add session to store
        store.addEntry(new Entry(tlsCertCN, session));
    }

    @OnMessage
    public String onMessage(String message, Session session, @PathParam("tlsCertCN") String tlsCertCN) {
        // Handle incoming message
        System.out.println("Received message from " + tlsCertCN + ": " + message);
        // Echo the message back
        return "Echo from " + tlsCertCN + ": " + message;
    }

    @OnClose
    public void onClose(Session session, @PathParam("tlsCertCN") String tlsCertCN) {
        // Remove session from store
        store.removeEntry(tlsCertCN, session);
        // Handle connection close
        System.out.println("WebSocket closed for tlsCertCN: " + tlsCertCN);
    }
}
