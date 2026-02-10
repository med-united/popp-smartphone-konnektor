package de.servicehealth.popp.systemtests.cetp;

import java.util.List;

import de.gematik.ws.conn.eventservice.v7.Event;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import jakarta.xml.bind.JAXBContext;

/**
 * Decoder for CETP protocol
 * Reads: 4 bytes header + 4 bytes integer (length) + N bytes payload
 */
public class CetpProtocolDecoder extends ByteToMessageDecoder {

    private static final int HEADER_SIZE = 4;
    private static final int LENGTH_FIELD_SIZE = 4;
    private static final int MIN_FRAME_SIZE = HEADER_SIZE + LENGTH_FIELD_SIZE;

    static JAXBContext jaxbContext;
    static {
        try {
            jaxbContext = JAXBContext.newInstance(Event.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JAXBContext", e);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Wait until we have at least the header + length field
        if (in.readableBytes() < MIN_FRAME_SIZE) {
            return;
        }

        // Mark the current reader index
        in.markReaderIndex();

        // Read 4-byte header
        byte[] header = new byte[HEADER_SIZE];
        in.readBytes(header);

        // Read 4-byte integer (payload length)
        int payloadLength = in.readInt();

        // Validate payload length (prevent excessive memory allocation)
        if (payloadLength < 0 || payloadLength > 10 * 1024 * 1024) { // 10MB max
            throw new IllegalArgumentException("Invalid payload length: " + payloadLength);
        }

        // Check if we have enough bytes for the complete payload
        if (in.readableBytes() < payloadLength) {
            // Not enough data yet, reset reader index and wait for more data
            in.resetReaderIndex();
            return;
        }

        // Read the payload
        byte[] payload = new byte[payloadLength];
        in.readBytes(payload);

        var message = jaxbContext.createUnmarshaller().unmarshal(new java.io.ByteArrayInputStream(payload));

        out.add(message);
    }
}
