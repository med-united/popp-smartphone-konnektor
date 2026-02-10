package de.servicehealth.popp.systemtests.cetp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.List;

/**
 * CETP (Custom Encrypted Transfer Protocol) Server
 * 
 * Protocol Format:
 * - 4 bytes: Protocol header/magic bytes
 * - 4 bytes: Integer (payload length in bytes)
 * - N bytes: Payload data (where N is the integer value read)
 */
public class CetpServer {

    private final int port;
    private final SslContext sslContext;

    public CetpServer(int port) throws CertificateException, SSLException {
        this.port = port;
        // Create self-signed certificate for TLS
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        this.sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
    }

    public CetpServer(int port, SslContext sslContext) {
        this.port = port;
        this.sslContext = sslContext;
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            // Add SSL handler first
                            pipeline.addLast(sslContext.newHandler(ch.alloc()));

                            // Add CETP protocol decoder
                            pipeline.addLast(new CetpProtocolDecoder());

                            // Add business logic handler
                            pipeline.addLast(new CetpServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Bind and start to accept incoming connections
            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("CETP Server started on port " + port + " with TLS enabled");

            // Wait until the server socket is closed
            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}