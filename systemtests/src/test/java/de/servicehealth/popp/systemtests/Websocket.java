package de.servicehealth.popp.systemtests;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.junit.jupiter.api.Test;

import jakarta.websocket.DeploymentException;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Websocket {
	private static final Logger log = LoggerFactory.getLogger(Websocket.class);

	@Test
	public void registerEgK() {

		long start = System.currentTimeMillis();
		initializeJULLoggingBridge();
		log.info("Run took {} ms", (System.currentTimeMillis() - start));
		try {
			// System.setProperty("javax.net.debug", "all");

			// WebSocket-Verbindung herstellen
			ClientManager client = ClientManager.createClient();
			configureSSL(client);
			CardlinkWebSocketClientEndpoint cardlinkWebSocketClientEndpoint = null;
			cardlinkWebSocketClientEndpoint = new CardlinkWebSocketClientEndpoint();
			client.connectToServer(cardlinkWebSocketClientEndpoint, new URI("wss://localhost:8443/websocket/null"));
			
			try {
				cardlinkWebSocketClientEndpoint.closed.await(120, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log.error("Got interrupted", e);
			}
			
			
		} catch (URISyntaxException | DeploymentException | IOException e) {
			log.error("Can't connect to card", e);
		}
	}

    private static void configureSSL(ClientManager client) {
		String propertyFileLocation = System.getProperty("ssl.propertyFile", "ssl.properties");
		File file = new File(propertyFileLocation);
		if(file.exists()) {
			Properties properties = new Properties();
			try {
				FileInputStream fis = new FileInputStream(file);
				properties.load(fis);
				SslContextConfigurator sslContextConfigurator = new SslContextConfigurator();

				if(properties.containsKey("ssl.trustStoreFile")) {
					sslContextConfigurator.setTrustStoreFile(properties.getProperty("ssl.trustStoreFile"));
				}
				if(properties.containsKey("ssl.trustStorePassword")) {
					sslContextConfigurator.setTrustStorePassword(properties.getProperty("ssl.trustStorePassword"));
				}
				if(properties.containsKey("ssl.trustStoreType")) {
					sslContextConfigurator.setTrustStoreType(properties.getProperty("ssl.trustStoreType"));
				}
				if(properties.containsKey("ssl.keyStoreFile")) {
					sslContextConfigurator.setKeyStoreFile(properties.getProperty("ssl.keyStoreFile"));
				}
				if(properties.containsKey("ssl.keyStorePassword")) {
					sslContextConfigurator.setKeyStorePassword(properties.getProperty("ssl.keyStorePassword"));
				}
				if(properties.containsKey("ssl.keyStoreType")) {
					sslContextConfigurator.setKeyStoreType(properties.getProperty("ssl.keyStoreType"));
				}
				SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sslContextConfigurator, true, false, false);

				client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
				fis.close();
			} catch (IOException e) {
				log.error("SSL configuration failed", e);
			}
		} else {
		 try {
				// Create a TrustManager that does not validate certificate chains
				javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
					new javax.net.ssl.X509TrustManager() {
						public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
						public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
						public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
					}
				};
				javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
				sc.init(null, trustAllCerts, new java.security.SecureRandom());
				SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sc);
				client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
			} catch (Exception e) {
				log.error("Failed to configure SSLEngine to accept all certificates", e);
			}
		}
	}

	private static void initializeJULLoggingBridge() {
		// We have some libraries that make use of java.util.logging but our project uses SLF4J+logback
		// Hence, we make JUL redirect to our logging system. See: https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
		// 1. Remove all preexisting JUL handlers as only SLF4J+logback shall produce logs.
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		// 2. Install the redirection JUL->SLF4J
		SLF4JBridgeHandler.install();
	}

}