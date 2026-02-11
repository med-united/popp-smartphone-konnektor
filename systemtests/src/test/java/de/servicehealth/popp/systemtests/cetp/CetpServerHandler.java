package de.servicehealth.popp.systemtests.cetp;

import java.net.URI;

import org.glassfish.tyrus.client.ClientManager;

import de.gematik.ws.conn.eventservice.v7.Event;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDService;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.servicehealth.popp.systemtests.CardlinkAVSWebSocketClientEndpoint;
import de.servicehealth.popp.systemtests.WebsocketTest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
     * Handler for processing CETP messages
     */
public class CetpServerHandler extends SimpleChannelInboundHandler<Event> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Event msg) throws Exception {
            try {
                VSDServicePortType vsdServicePortType = WebsocketTest.create(() -> new VSDService().getVSDServicePort(),"https://localhost:9443/services/VSDService/v5.2");
                var parameter = new de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD();
                var contextType = new de.gematik.ws.conn.connectorcontext.v2.ContextType();
                parameter.setContext(contextType);
                parameter.setEhcHandle(msg.getMessage().getParameter().stream()
                    .filter(s -> "CardHandle".equals(s.getKey())).findFirst().orElseThrow(() -> new IllegalArgumentException("Missing CardHandle parameter in event message")).getValue());
               vsdServicePortType.readVSD(parameter);

               var client = ClientManager.createClient();
			   WebsocketTest.configureSSL(client);
			   var cardlinkAVSWebSocketClientEndpoint = new CardlinkAVSWebSocketClientEndpoint();
			   client.connectToServer(cardlinkAVSWebSocketClientEndpoint, new URI("wss://localhost:9443/websocket/null"));
               Thread.sleep(5000); // Wait for WebSocket communication to complete before counting down the latch
               WebsocketTest.readVSDSend.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Client connected: " + ctx.channel().remoteAddress());
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
