package de.servicehealth.popp.systemtests;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

@ClientEndpoint
public class CardlinkAVSWebSocketClientEndpoint {

    private static final Logger log = LoggerFactory.getLogger(CardlinkAVSWebSocketClientEndpoint.class);

    String cardSessionId;

    public CardlinkAVSWebSocketClientEndpoint(String cardSessionId) {
        this.cardSessionId = cardSessionId;
    }

    @OnOpen
    public void onOpen(Session session) {
        try {
            String eRezeptTokensFromAVS = new String(getClass().getResourceAsStream("/eRezeptTokensFromAVS.json").readAllBytes());
            eRezeptTokensFromAVS = eRezeptTokensFromAVS.replace("537e7eb7-82cd-4af0-90f2-3e514109f542", cardSessionId);
            System.out.println(eRezeptTokensFromAVS);
            session.getBasicRemote().sendText(eRezeptTokensFromAVS);
            String eRezeptBundleFromAVS = new String(getClass().getResourceAsStream("/eRezeptBundleFromAVS.json").readAllBytes());
            eRezeptBundleFromAVS = eRezeptBundleFromAVS.replace("537e7eb7-82cd-4af0-90f2-3e514109f542", cardSessionId);
            System.out.println(eRezeptBundleFromAVS);
            session.getBasicRemote().sendText(eRezeptBundleFromAVS);
            System.out.println("AVS Websocket opened, message send");
             // Close the session after sending the messages
            // session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @OnMessage
    public void onMessage(String message) {
        log.info("AVS Received: {}", message);
    }

    @OnError
    public void onError(Throwable t) {
        log.error("AVS Websocket client onError called", t);
    }

    @OnClose
    public void onClose(Session session) {
        log.info("AVS Websocket closed");
    }

}