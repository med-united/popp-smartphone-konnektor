package de.servicehealth.popp.cardlink;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.logging.Logger;

import de.servicehealth.cardlink.model.RegisterEgkPayload;
import de.servicehealth.popp.session.Entry;
import de.servicehealth.popp.session.Store;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket/{tlsCertCN}")
public class Webservice {

    private static final Logger LOG = Logger.getLogger(Webservice.class.getName());

    @Inject
    private Store store;

    @OnOpen
    public void onOpen(Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {
        // Handle new connection
        LOG.info("WebSocket opened for tlsCertCN: " + tlsCertCN);
        // Add session to store
        store.addEntry(new Entry(tlsCertCN, webSocketSession));
    }

    @OnMessage
    public void onMessage(String message, Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {
        // Handle incoming message
        LOG.fine("Received message from " + tlsCertCN + ": " + message);
        // Echo the message back
        try {

            JsonStructure jsonStructure = Json.createReader(new ByteArrayInputStream(message.getBytes())).read();
            if(!(jsonStructure instanceof JsonArray)) {
                LOG.warning("Received JSON is not an array but is: "+(jsonStructure == null ? "null" : jsonStructure.getClass().getName()));
                return;
            }
            JsonArray jsonArray = (JsonArray) jsonStructure;
            if(jsonArray.size() < 1) {
                LOG.warning("Received JSON array is empty");
                return;
            }
            if(jsonArray.size() < 2) {
                LOG.warning("Received JSON array has no cardSessionId");
                return;
            }
            JsonObject jsonObject = jsonArray.getJsonObject(0);
            String cardSessionId = jsonArray.getString(1); // cardSessionId
            String correlationId = null;
            if(jsonArray.size() < 3) {
                LOG.warning("Received JSON array has no correlationId");
            } else {
                correlationId = jsonArray.getString(2); // correlationId
                LOG.fine("Correlation ID: " + correlationId);
            }

            if(jsonObject.containsKey("type")) {
                String type = jsonObject.getString("type");
                LOG.fine("Message type: " + type);
                // [{"type": "registerEGK", "payload": "eyJjYXJkU2Vzc2lvbklkIjoiNTM3ZTdlYjctODJjZC00YWYwLTkwZjItM2U1MTQxMDlmNTQyIiwiZ2RvIjoiV2dxQUoyaURFUUFBRldFViIsImF0ciI6IjRCRUNBZ2dKQWdNQWdBSUNBZ2dKQWdJSUNWOVNESUJtQlVSRlNVUk5jNVloODlBREJBWUEwaEJFUlVsR1dGTk1Rek15UjBSQkJBQUEweEJFUlVsRVRVMUlRMGRmUnpJeUFnSUcxQkJFUlVsRVRVVklRMTg1TURBd0F3QUYxaEJFUlVsRVRWQldWbFkxTGpBd0FRQUF6eFhQejgvUHo4L1B6OC9QejgvUHo4L1B6OC9Qejg4PSIsImNhcmRWZXJzaW9uIjoiN3l2QUF3SUFBTUVEQkFVQ3doQkVSVWxFVFVWSVExODVNREF3QXdBRnhBTUJBQURGQXdJQUFNY0RBUUFBIiwieDUwOUF1dGhSU0EiOiJNSUlFOWpDQ0E5NmdBd0lCQWdJSEExem9VaERNUURBTkJna3Foa2lHOXcwQkFRc0ZBRENCbGpFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReFJUQkRCZ05WQkFzTVBFVnNaV3QwY205dWFYTmphR1VnUjJWemRXNWthR1ZwZEhOcllYSjBaUzFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5Ma1ZIU3kxRFFUUXhJRlJGVTFRdFQwNU1XVEFlRncweU5EQTBNREl3TURBd01EQmFGdzB5T1RBME1ERXlNelU1TlRsYU1JSHRNUXN3Q1FZRFZRUUdFd0pFUlRFZE1Cc0dBMVVFQ2d3VVZHVnpkQ0JIUzFZdFUxWk9UMVF0VmtGTVNVUXhFakFRQmdOVkJBc01DVEV3T1RVd01EazJPVEVUTUJFR0ExVUVDd3dLV0RFeE1EVTJOemsxTlRFVE1CRUdBMVVFQkF3S1ZHRnVaMlZ5dzdCaGJERThNRG9HQTFVRUtnd3pRVzV1YVd0aElGWmxjbUVnVFdWc2FYTnpZU0JDY25WdWFHbHNaQ0JCY0doeWIyUnBkR1VnUm5KbGFXWnlZWFVnZG05dU1VTXdRUVlEVlFRREREcEJibTVwYTJFZ1ZtVnlZU0JOWld4cGMzTmhJRUl1SUVFdUlFWnlaV2xtY21GMUlIWnZiaUJVWVc1blpYTERzR0ZzVkVWVFZDMVBUa3haTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF6eGR6N2svSGtKa0hYWm9oeldiKzZWVDJvNVRXai9kM0R0Ynd3bU4zYWJqeEtsZDJpeTZzL0UrSEhVQUZyRHBXR2pxUSs3a3d5QXQvTWp1aEE2aElxVW9RaHU3MThLRnFPaHZpT2FMMGpQalJJc2s2Ky8ydFljc2xPbmNSd29XWXkwVzhuZTVlWW90bTk0YW1YM0JJVFpvTTRnS1k4cnNsSjNLbTlQMDZxNTd5RjFUaFdCY0F6UzJtOXFrUDFzbmZtYmE5OG1CQjQzTktCV1hCN3NLRGxGcjZXV3VNNmhEVjVYdzQrU1JJdTA0UE1iekxmakRtKzIzWFdOTmticFdHMk9sbUd0d0l3RWlLSTBDZHVaKzVqTTFYckJBYkNxQWxlblpueDErWEx1cWJnMGNvTHRTZW5CQnc0Y3pLZ0U5bE5TSUVxdHpGRGtjdWNMS25TSXYxWFFJREFRQUJvNEh2TUlIc01BNEdBMVVkRHdFQi93UUVBd0lIZ0RBNEJnZ3JCZ0VGQlFjQkFRUXNNQ293S0FZSUt3WUJCUVVITUFHR0hHaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2YjJOemNDOHdId1lEVlIwakJCZ3dGb0FVR04xMThDRjRMWVk4QzVvYWZWdEExbGlKOWhZd0hRWURWUjBPQkJZRUZGVmozZU1jS2hlb1BYY21YUFYxcUoxek9WaE5NQ0FHQTFVZElBUVpNQmN3Q2dZSUtvSVVBRXdFZ1NNd0NRWUhLb0lVQUV3RVJqQXdCZ1VySkFnREF3UW5NQ1V3SXpBaE1COHdIVEFRREE1V1pYSnphV05vWlhKMFpTOHRjakFKQmdjcWdoUUFUQVF4TUF3R0ExVWRFd0VCL3dRQ01BQXdEUVlKS29aSWh2Y05BUUVMQlFBRGdnRUJBTWJOWnJaWlcvSWtIcHBFOUxsRWt4ckNVNS9kY1F3K2tGQ2xNUDBTZUhBaCs2M3dSZWo4VU5yOG9QSmJ6aUZsYVRWWE5yaWprenQ2VUd6RGxhMVJBMDJ4SHVHMlpPdGw5d2lqWVE1aUJuTG5FNlBwTDdlVnBlNVhqNVdId1NpY25TMFVJL3FyYkQ1aVAwVFowSStBcUlGT1hIL1h1citJU2hvbXZnL25wanF6YUs0dWg1SkExcTVBYVQrcU5YNk15MmlzT3RRR24zWUhmVnZzdkpwNzZLcEp2elk4bjcvenNweTBqUkNxVVRJQ0N4NjZoUFAwWlZsc2RQdWFPSFV2TkxwMllTbzU0QVJhVU9ScVlvSFl4eFptcjV2RkxrcjRmUTBNdkNpMW5yNWUwMk10bFRtZnZQTHVPaGZPVmlnNTdpOXBwbE8zaHgrY2JjYjR3UkU5cDVRPSIsIng1MDlBdXRoRUNDIjoiTUlJRGJqQ0NBeFNnQXdJQkFnSUhBdjhDcGJDckVqQUtCZ2dxaGtqT1BRUURBakNCbGpFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReFJUQkRCZ05WQkFzTVBFVnNaV3QwY205dWFYTmphR1VnUjJWemRXNWthR1ZwZEhOcllYSjBaUzFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5Ma1ZIU3kxRFFUVXhJRlJGVTFRdFQwNU1XVEFlRncweU5EQTBNREl3TURBd01EQmFGdzB5T1RBME1ERXlNelU1TlRsYU1JSHRNUXN3Q1FZRFZRUUdFd0pFUlRFZE1Cc0dBMVVFQ2d3VVZHVnpkQ0JIUzFZdFUxWk9UMVF0VmtGTVNVUXhFakFRQmdOVkJBc01DVEV3T1RVd01EazJPVEVUTUJFR0ExVUVDd3dLV0RFeE1EVTJOemsxTlRFVE1CRUdBMVVFQkF3S1ZHRnVaMlZ5dzdCaGJERThNRG9HQTFVRUtnd3pRVzV1YVd0aElGWmxjbUVnVFdWc2FYTnpZU0JDY25WdWFHbHNaQ0JCY0doeWIyUnBkR1VnUm5KbGFXWnlZWFVnZG05dU1VTXdRUVlEVlFRREREcEJibTVwYTJFZ1ZtVnlZU0JOWld4cGMzTmhJRUl1SUVFdUlFWnlaV2xtY21GMUlIWnZiaUJVWVc1blpYTERzR0ZzVkVWVFZDMVBUa3haTUZvd0ZBWUhLb1pJemowQ0FRWUpLeVFEQXdJSUFRRUhBMElBQkkxQ0lORHZkQ3NWbXprUks3VWovb3VsVS81Q0svd1d3STBvUzZOSVMyKzlrL0tGVUthVkhRbW1VS2JES1dzNWFNemxCY1IvZDBybndJR2F1b0x3WnZpamdmSXdnZTh3T3dZSUt3WUJCUVVIQVFFRUx6QXRNQ3NHQ0NzR0FRVUZCekFCaGg5b2RIUndPaTh2WldoallTNW5aVzFoZEdsckxtUmxMMlZqWXkxdlkzTndNQ0FHQTFVZElBUVpNQmN3Q2dZSUtvSVVBRXdFZ1NNd0NRWUhLb0lVQUV3RVJqQXdCZ1VySkFnREF3UW5NQ1V3SXpBaE1COHdIVEFRREE1V1pYSnphV05vWlhKMFpTOHRjakFKQmdjcWdoUUFUQVF4TUE0R0ExVWREd0VCL3dRRUF3SUhnREFNQmdOVkhSTUJBZjhFQWpBQU1COEdBMVVkSXdRWU1CYUFGSFRwK1JTRDRRdm1FZllxckpmczJhK1pROEh3TUIwR0ExVWREZ1FXQkJSZGJRTzk5K0UvTi8zUWR1dXVRUHdXTVl5cWZ6QUtCZ2dxaGtqT1BRUURBZ05JQURCRkFpRUFxSXJwZE4yeU43Rzl6R3pMVjk5emdvcHA2OWpianNwKzdoTlNVa29QSmhJQ0lGV3hJYlB4TlN2NmtzVWVSSjdzZ2RzZTFLRGR5ZUZLaEhRem1NeVF0UThZIiwiY3ZjQXV0aCI6ImZ5R0IybjlPZ1pOZktRRndRZ2hFUlVkWVdCRUNJMzlKU3dZR0t5UURCUU1CaGtFRU9nckFtYjhkc256WGYveHlmODhZOU1VSFhpdWVVd0FPQ3Eyb2pTTUJla1o3YkthUElqSnpPN1hJNWxUOUh3bmt4K0ZJWU9FUlZoVVZIZkpOSDRxNXAxOGdEQUFKZ0Nkb2d4RUFBQlZoRlg5TUV3WUlLb0lVQUV3RWdSaFRCd0FBQUFBQUFBQmZKUVlDQkFBRUFBSmZKQVlDQ1FBRUFBRmZOMEFoa20rVDJWTitaVzQ0V2FuTmc4MHFraVBqejNMYkZrUkRNYkF4RlVIb09xVTFaY2p2ZEpTUFhFazRaY2FxbXJtTEJYSGdJQnlGUUU0NURQMlNINTJkIiwiY3ZjQ0EiOiJmeUdCMkg5T2daRmZLUUZ3UWdoRVJVZFlXSWNDSW45SlRRWUlLb1pJemowRUF3S0dRUVFvUUZvTXpGeFR0bmdEVnFVVUhyUi83VjlXdmtTOEl2SUViOEJUL3R2Q1hsRGlTbTFxK1Z3Yy91bEplc3pqV2FKVDk5QzNxNjZsMGFZdDREQVVYd3lYWHlBSVJFVkhXRmdSQWlOL1RCTUdDQ3FDRkFCTUJJRVlVd2VBQUFBQUFBQUFYeVVHQWdNQUJ3TUJYeVFHQXdFQUJ3TUFYemRBVE5KZ3dJQTdFbG9BRzZnYnFmTGlzVGtONVBGR2tjZ2lvb3pIZHFHRzE3cC9DSEJNSi8zTnJySDRza09qZVhiUE43OThFaGhZMFBCQm5lZ3lGNk9WM2c9PSIsImNsaWVudCI6IkNPTSJ9"}, "537e7eb7-82cd-4af0-90f2-3e514109f542", "7956ec67-a28b-4265-ad26-2645e7458095"]
                if(type.equals("registerEGK")) {
                    // Handle registerEGK message
                    store.registerEGK(tlsCertCN, webSocketSession, parseEGKPayload(new String(Base64.getDecoder().decode(jsonObject.getString("payload")))), cardSessionId);
                }
            } else {
                LOG.fine("No type field in message");
            }
            // webSocketSession.getBasicRemote().sendText("Echo from " + tlsCertCN + ": " + message);
        } catch (Exception e) {
            LOG.severe("Error sending message: " + e.getMessage());
        }
    }

    private RegisterEgkPayload parseEGKPayload(String string) {
        return JsonbBuilder.create().fromJson(new ByteArrayInputStream(string.getBytes()), RegisterEgkPayload.class);
    }

    @OnClose
    public void onClose(Session webSocketSession, @PathParam("tlsCertCN") String tlsCertCN) {
        // Remove session from store
        store.removeEntry(tlsCertCN, webSocketSession);
        // Handle connection close
        LOG.info("WebSocket closed for tlsCertCN: " + tlsCertCN);
    }
}
