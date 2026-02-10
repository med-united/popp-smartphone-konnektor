package de.servicehealth.popp.systemtests.cetp;

import de.gematik.ws.conn.eventservice.v7.Event;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDService;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.servicehealth.popp.systemtests.WebsocketTest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
     * Handler for processing CETP messages
     */
public class CetpServerHandler extends SimpleChannelInboundHandler<Event> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Event msg) throws Exception {
            VSDServicePortType vsdServicePortType = WebsocketTest.create(() -> new VSDService().getVSDServicePort(),"https://localhost:8443/services/VSDService/v5.2");
            var parameter = new de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD();
            var contextType = new de.gematik.ws.conn.connectorcontext.v2.ContextType();
            parameter.setContext(contextType);
            parameter.setEhcHandle(msg.getMessage().getParameter().stream()
                .filter(s -> "CardHandle".equals(s)).findFirst().orElseThrow(() -> new IllegalArgumentException("Missing CardHandle parameter in event message")).getValue());
           vsdServicePortType.readVSD(parameter);
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
        
        private static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02X ", b));
            }
            return sb.toString().trim();
        }
    }
