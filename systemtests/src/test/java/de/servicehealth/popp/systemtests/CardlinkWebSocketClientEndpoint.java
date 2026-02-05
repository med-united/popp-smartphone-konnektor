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
public class CardlinkWebSocketClientEndpoint {

    private static final Logger log = LoggerFactory.getLogger(CardlinkWebSocketClientEndpoint.class);
    String cardSessionId = UUID.randomUUID().toString();
    String smsCode;
    private String correlationId;
    Session session;
    final CountDownLatch closed = new CountDownLatch(1);


    public CardlinkWebSocketClientEndpoint() {
    }



    public String generateRegisterEgk() {
        return "[{\"type\": \"registerEGK\", \"payload\": \"eyJjYXJkU2Vzc2lvbklkIjoiNTM3ZTdlYjctODJjZC00YWYwLTkwZjItM2U1MTQxMDlmNTQyIiwiZ2RvIjoiV2dxQUoyaURFUUFBRldFViIsImF0ciI6IjRCRUNBZ2dKQWdNQWdBSUNBZ2dKQWdJSUNWOVNESUJtQlVSRlNVUk5jNVloODlBREJBWUEwaEJFUlVsR1dGTk1Rek15UjBSQkJBQUEweEJFUlVsRVRVMUlRMGRmUnpJeUFnSUcxQkJFUlVsRVRVVklRMTg1TURBd0F3QUYxaEJFUlVsRVRWQldWbFkxTGpBd0FRQUF6eFhQejgvUHo4L1B6OC9QejgvUHo4L1B6OC9Qejg4PSIsImNhcmRWZXJzaW9uIjoiN3l2QUF3SUFBTUVEQkFVQ3doQkVSVWxFVFVWSVExODVNREF3QXdBRnhBTUJBQURGQXdJQUFNY0RBUUFBIiwieDUwOUF1dGhSU0EiOiJNSUlFOWpDQ0E5NmdBd0lCQWdJSEExem9VaERNUURBTkJna3Foa2lHOXcwQkFRc0ZBRENCbGpFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReFJUQkRCZ05WQkFzTVBFVnNaV3QwY205dWFYTmphR1VnUjJWemRXNWthR1ZwZEhOcllYSjBaUzFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5Ma1ZIU3kxRFFUUXhJRlJGVTFRdFQwNU1XVEFlRncweU5EQTBNREl3TURBd01EQmFGdzB5T1RBME1ERXlNelU1TlRsYU1JSHRNUXN3Q1FZRFZRUUdFd0pFUlRFZE1Cc0dBMVVFQ2d3VVZHVnpkQ0JIUzFZdFUxWk9UMVF0VmtGTVNVUXhFakFRQmdOVkJBc01DVEV3T1RVd01EazJPVEVUTUJFR0ExVUVDd3dLV0RFeE1EVTJOemsxTlRFVE1CRUdBMVVFQkF3S1ZHRnVaMlZ5dzdCaGJERThNRG9HQTFVRUtnd3pRVzV1YVd0aElGWmxjbUVnVFdWc2FYTnpZU0JDY25WdWFHbHNaQ0JCY0doeWIyUnBkR1VnUm5KbGFXWnlZWFVnZG05dU1VTXdRUVlEVlFRREREcEJibTVwYTJFZ1ZtVnlZU0JOWld4cGMzTmhJRUl1SUVFdUlFWnlaV2xtY21GMUlIWnZiaUJVWVc1blpYTERzR0ZzVkVWVFZDMVBUa3haTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF6eGR6N2svSGtKa0hYWm9oeldiKzZWVDJvNVRXai9kM0R0Ynd3bU4zYWJqeEtsZDJpeTZzL0UrSEhVQUZyRHBXR2pxUSs3a3d5QXQvTWp1aEE2aElxVW9RaHU3MThLRnFPaHZpT2FMMGpQalJJc2s2Ky8ydFljc2xPbmNSd29XWXkwVzhuZTVlWW90bTk0YW1YM0JJVFpvTTRnS1k4cnNsSjNLbTlQMDZxNTd5RjFUaFdCY0F6UzJtOXFrUDFzbmZtYmE5OG1CQjQzTktCV1hCN3NLRGxGcjZXV3VNNmhEVjVYdzQrU1JJdTA0UE1iekxmakRtKzIzWFdOTmticFdHMk9sbUd0d0l3RWlLSTBDZHVaKzVqTTFYckJBYkNxQWxlblpueDErWEx1cWJnMGNvTHRTZW5CQnc0Y3pLZ0U5bE5TSUVxdHpGRGtjdWNMS25TSXYxWFFJREFRQUJvNEh2TUlIc01BNEdBMVVkRHdFQi93UUVBd0lIZ0RBNEJnZ3JCZ0VGQlFjQkFRUXNNQ293S0FZSUt3WUJCUVVITUFHR0hHaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2YjJOemNDOHdId1lEVlIwakJCZ3dGb0FVR04xMThDRjRMWVk4QzVvYWZWdEExbGlKOWhZd0hRWURWUjBPQkJZRUZGVmozZU1jS2hlb1BYY21YUFYxcUoxek9WaE5NQ0FHQTFVZElBUVpNQmN3Q2dZSUtvSVVBRXdFZ1NNd0NRWUhLb0lVQUV3RVJqQXdCZ1VySkFnREF3UW5NQ1V3SXpBaE1COHdIVEFRREE1V1pYSnphV05vWlhKMFpTOHRjakFKQmdjcWdoUUFUQVF4TUF3R0ExVWRFd0VCL3dRQ01BQXdEUVlKS29aSWh2Y05BUUVMQlFBRGdnRUJBTWJOWnJaWlcvSWtIcHBFOUxsRWt4ckNVNS9kY1F3K2tGQ2xNUDBTZUhBaCs2M3dSZWo4VU5yOG9QSmJ6aUZsYVRWWE5yaWprenQ2VUd6RGxhMVJBMDJ4SHVHMlpPdGw5d2lqWVE1aUJuTG5FNlBwTDdlVnBlNVhqNVdId1NpY25TMFVJL3FyYkQ1aVAwVFowSStBcUlGT1hIL1h1citJU2hvbXZnL25wanF6YUs0dWg1SkExcTVBYVQrcU5YNk15MmlzT3RRR24zWUhmVnZzdkpwNzZLcEp2elk4bjcvenNweTBqUkNxVVRJQ0N4NjZoUFAwWlZsc2RQdWFPSFV2TkxwMllTbzU0QVJhVU9ScVlvSFl4eFptcjV2RkxrcjRmUTBNdkNpMW5yNWUwMk10bFRtZnZQTHVPaGZPVmlnNTdpOXBwbE8zaHgrY2JjYjR3UkU5cDVRPSIsIng1MDlBdXRoRUNDIjoiTUlJRGJqQ0NBeFNnQXdJQkFnSUhBdjhDcGJDckVqQUtCZ2dxaGtqT1BRUURBakNCbGpFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReFJUQkRCZ05WQkFzTVBFVnNaV3QwY205dWFYTmphR1VnUjJWemRXNWthR1ZwZEhOcllYSjBaUzFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5Ma1ZIU3kxRFFUVXhJRlJGVTFRdFQwNU1XVEFlRncweU5EQTBNREl3TURBd01EQmFGdzB5T1RBME1ERXlNelU1TlRsYU1JSHRNUXN3Q1FZRFZRUUdFd0pFUlRFZE1Cc0dBMVVFQ2d3VVZHVnpkQ0JIUzFZdFUxWk9UMVF0VmtGTVNVUXhFakFRQmdOVkJBc01DVEV3T1RVd01EazJPVEVUTUJFR0ExVUVDd3dLV0RFeE1EVTJOemsxTlRFVE1CRUdBMVVFQkF3S1ZHRnVaMlZ5dzdCaGJERThNRG9HQTFVRUtnd3pRVzV1YVd0aElGWmxjbUVnVFdWc2FYTnpZU0JDY25WdWFHbHNaQ0JCY0doeWIyUnBkR1VnUm5KbGFXWnlZWFVnZG05dU1VTXdRUVlEVlFRREREcEJibTVwYTJFZ1ZtVnlZU0JOWld4cGMzTmhJRUl1SUVFdUlFWnlaV2xtY21GMUlIWnZiaUJVWVc1blpYTERzR0ZzVkVWVFZDMVBUa3haTUZvd0ZBWUhLb1pJemowQ0FRWUpLeVFEQXdJSUFRRUhBMElBQkkxQ0lORHZkQ3NWbXprUks3VWovb3VsVS81Q0svd1d3STBvUzZOSVMyKzlrL0tGVUthVkhRbW1VS2JES1dzNWFNemxCY1IvZDBybndJR2F1b0x3WnZpamdmSXdnZTh3T3dZSUt3WUJCUVVIQVFFRUx6QXRNQ3NHQ0NzR0FRVUZCekFCaGg5b2RIUndPaTh2WldoallTNW5aVzFoZEdsckxtUmxMMlZqWXkxdlkzTndNQ0FHQTFVZElBUVpNQmN3Q2dZSUtvSVVBRXdFZ1NNd0NRWUhLb0lVQUV3RVJqQXdCZ1VySkFnREF3UW5NQ1V3SXpBaE1COHdIVEFRREE1V1pYSnphV05vWlhKMFpTOHRjakFKQmdjcWdoUUFUQVF4TUE0R0ExVWREd0VCL3dRRUF3SUhnREFNQmdOVkhSTUJBZjhFQWpBQU1COEdBMVVkSXdRWU1CYUFGSFRwK1JTRDRRdm1FZllxckpmczJhK1pROEh3TUIwR0ExVWREZ1FXQkJSZGJRTzk5K0UvTi8zUWR1dXVRUHdXTVl5cWZ6QUtCZ2dxaGtqT1BRUURBZ05JQURCRkFpRUFxSXJwZE4yeU43Rzl6R3pMVjk5emdvcHA2OWpianNwKzdoTlNVa29QSmhJQ0lGV3hJYlB4TlN2NmtzVWVSSjdzZ2RzZTFLRGR5ZUZLaEhRem1NeVF0UThZIiwiY3ZjQXV0aCI6ImZ5R0IybjlPZ1pOZktRRndRZ2hFUlVkWVdCRUNJMzlKU3dZR0t5UURCUU1CaGtFRU9nckFtYjhkc256WGYveHlmODhZOU1VSFhpdWVVd0FPQ3Eyb2pTTUJla1o3YkthUElqSnpPN1hJNWxUOUh3bmt4K0ZJWU9FUlZoVVZIZkpOSDRxNXAxOGdEQUFKZ0Nkb2d4RUFBQlZoRlg5TUV3WUlLb0lVQUV3RWdSaFRCd0FBQUFBQUFBQmZKUVlDQkFBRUFBSmZKQVlDQ1FBRUFBRmZOMEFoa20rVDJWTitaVzQ0V2FuTmc4MHFraVBqejNMYkZrUkRNYkF4RlVIb09xVTFaY2p2ZEpTUFhFazRaY2FxbXJtTEJYSGdJQnlGUUU0NURQMlNINTJkIiwiY3ZjQ0EiOiJmeUdCMkg5T2daRmZLUUZ3UWdoRVJVZFlXSWNDSW45SlRRWUlLb1pJemowRUF3S0dRUVFvUUZvTXpGeFR0bmdEVnFVVUhyUi83VjlXdmtTOEl2SUViOEJUL3R2Q1hsRGlTbTFxK1Z3Yy91bEplc3pqV2FKVDk5QzNxNjZsMGFZdDREQVVYd3lYWHlBSVJFVkhXRmdSQWlOL1RCTUdDQ3FDRkFCTUJJRVlVd2VBQUFBQUFBQUFYeVVHQWdNQUJ3TUJYeVFHQXdFQUJ3TUFYemRBVE5KZ3dJQTdFbG9BRzZnYnFmTGlzVGtONVBGR2tjZ2lvb3pIZHFHRzE3cC9DSEJNSi8zTnJySDRza09qZVhiUE43OThFaGhZMFBCQm5lZ3lGNk9WM2c9PSIsImNsaWVudCI6IkNPTSJ9\"}, \"537e7eb7-82cd-4af0-90f2-3e514109f542\", \"7956ec67-a28b-4265-ad26-2645e7458095\"]";
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        try {
            String textTemplate = "{0}"; // "Bitte geben Sie in der CardLink App folgenden Code ein: {0}";
            String requestSMSCode = buildMessage("requestSMSCode", "{\"senderId\": \"ws-sms\",\r\n        \"textTemplate\": \"" + textTemplate + "\",\r\n        \"phoneNumber\": \"\",\r\n        \"textReassignmentTemplate\": \"Ihre Gesundheitskarte {0} wurde der Telefonnummer {1} neu zugeordnet. Wenn Sie diese Telefonnummer kennen, ist alles in Ordnung. Wenn Ihre Karte gestohlen wurde, lassen Sie diese bitte von Ihrer Versicherung sperren.\"\r\n    }");
            log.info("Sending: {}", requestSMSCode);
            session.getBasicRemote().sendText(requestSMSCode);
        } catch (IOException e) {
            log.error("Sending requestSMSCode failed", e);
        }
    }

    private void sendConfirmSMSCodeAndRegisterEGK(Session session, String smsCode) throws IOException {
        String confirmSMSCode = buildMessage("confirmSMSCode", "{\"smsCode\": \"" + smsCode + "\"}");
        log.info("Sending: {}", confirmSMSCode);
        session.getBasicRemote().sendText(confirmSMSCode);

        String registerEgk = generateRegisterEgk();

        log.info("registerEGK: {}", registerEgk);
        session.getBasicRemote().sendText(registerEgk);
    }

    public String buildMessage(String type, String payload) {
        return "[{\"type\": \"" + type + "\", \"payload\": \"" + Base64.getEncoder().encodeToString(payload.getBytes()) + "\"}, \"" + cardSessionId + "\", \"" + UUID.randomUUID().toString() + "\"]";
    }

    @OnMessage
    public void onMessage(String message) {
        log.info("Received: {}", message);

        if (message.length() == 6) {
            try {
                sendConfirmSMSCodeAndRegisterEGK(session, message);
                return;
            } catch (IOException e) {
                log.error("confirmSMSandRegisterEGK failed", e);
            }
        }
        if (message.charAt(0) == '[' || message.charAt(0) == '{') {
            JsonValue value = Json.createReader(new StringReader(message)).readValue();
            if (value instanceof JsonArray jsonArray) {
                JsonObject firstMessage = jsonArray.get(0).asJsonObject();
                String result = decodePayload(firstMessage);
                log.info("DecodedPayload: " + result);

                if (jsonArray.size() == 3) {
                    correlationId = jsonArray.getString(2, "");
                }

                String type = firstMessage.getString("type");

                if ("sendAPDU".equals(type)) {
                    String payload = firstMessage.getString("payload");
                    String apduJson = new String(Base64.getDecoder().decode(payload));
                    JsonObject apduJsonObject = Json.createReader(new StringReader(apduJson)).readObject();
                    String apdu = apduJsonObject.getString("apdu");
                    byte[] apduBytes = Base64.getDecoder().decode(apdu);
                    byte[] response = sendCommand("Command " + apdu, apduBytes, true);

                    String responseBase64 = Base64.getEncoder().encodeToString(response);
                    String websocketMessage = "[{\"type\": \"sendAPDUResponse\", \"payload\": \"" + Base64.getEncoder().encodeToString(("{\"response\": \"" + responseBase64 + "\"}").getBytes()) + "\"}, \"" + cardSessionId + "\", \"" + correlationId + "\"]";
                    log.info("Sending: {}", websocketMessage);
                    try {
                        session.getBasicRemote().sendText(websocketMessage);
                    } catch (IOException e) {
                        log.error("Got an IOException when trying to send APDU response", e);
                    }
                } else if ("eRezeptBundlesFromAVS".equals(type)) {
                    try {
                        prettyPrintBundle(firstMessage);
                        session.close();
                    } catch (IOException e) {
                        log.error("Failed pretty printing eRezeptBundlesFromAVS", e);
                    }
                } else if ("genericErrorMessage".equals(type)) {
                    log.error("ERROR: {}", new String(Base64.getDecoder().decode(firstMessage.getString("payload"))));
                    try {
                        session.close();
                    } catch (IOException e) {
                        log.error("Got an IOException when trying to close session", e);
                    }
                }

            }
        } else {
            log.warn("Unknown message: {}", message);
        }
    }

    private static String decodePayload(JsonObject firstMessage) {
        String payloadLog = firstMessage.getString("payload", firstMessage.get("payload").toString());
        String payloadDecoded = payloadLog.equals("null") ? "null" : new String(Base64.getDecoder().decode(payloadLog), StandardCharsets.UTF_8);
        String result = firstMessage.toString().replaceFirst("(\"payload\":\\s*\")([^\"]+)(\")", "$1" + payloadDecoded + "$3");
        return result;
    }

    @OnError
    public void onError(Throwable t) {
        log.error("Websocket client onError called", t);
    }

    @OnClose
    public void onClose(Session session) {
        log.info("Websocket closed");
        closed.countDown();
    }


    private byte[] sendCommand(String action, byte[] command, boolean withResponseCode) {
        log.info("To card {}", HexFormat.of().formatHex(command));
        return null; // withResponseCode ? commandResponse.getBytes() : commandResponse.getData();
    }

    private void prettyPrintBundle(JsonObject firstMessage) {
        try {
            var payload = firstMessage.getString("payload");
            var jsonPayload = new String(Base64.getDecoder().decode(payload));
            try (var sr = new StringReader(jsonPayload);
                 var jr = Json.createReader(sr)) {
                var bundles = jr.readObject().get("bundles").asJsonArray();
                for (var bundle : bundles) {
                    prettyXml(bundle);
                }
            }
        } catch (Exception e) {
            log.error("Error printing XML", e);
        }
    }

    public static void prettyXml(JsonValue bundle) {
        try {
            String jsonEscaped = bundle.toString();
            String xml = StringEscapeUtils.unescapeJson(jsonEscaped.substring(1, jsonEscaped.length() - 1));
            Source xmlInput = new StreamSource(new StringReader(xml));
            try (StringWriter stringWriter = new StringWriter()) {
                StreamResult xmlOutput = new StreamResult(stringWriter);
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                transformerFactory.setAttribute("indent-number", 2);
                transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(xmlInput, xmlOutput);
                var prettyXml = xmlOutput.getWriter().toString();
                log.info("Pretty XML:\n{}", prettyXml);
            }
        } catch (Exception e) {
            log.error("Bundle is not not XML: {}", bundle);
        }
    }

}