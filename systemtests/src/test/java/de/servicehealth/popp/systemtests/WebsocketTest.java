package de.servicehealth.popp.systemtests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.namespace.QName;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.junit.jupiter.api.Test;

import jakarta.websocket.DeploymentException;
import jakarta.xml.ws.Service;

import org.slf4j.bridge.SLF4JBridgeHandler;

import de.gematik.ws.conn.cardservice.v8_2_1.SecureSendAPDU;
import de.gematik.ws.conn.cardservice.wsdl.v8_2.CardService;
import de.gematik.ws.conn.cardservice.wsdl.v8_2.CardServicePortType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventService;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage;

public class WebsocketTest<T> {

	@Test
	public void registerEgK() {
		System.out.println("Starting test case");
		long start = System.currentTimeMillis();
		try {
			// System.setProperty("javax.net.debug", "all");

			// WebSocket-Verbindung herstellen
			ClientManager client = ClientManager.createClient();
			configureSSL(client);
			CardlinkWebSocketClientEndpoint cardlinkWebSocketClientEndpoint = null;
			cardlinkWebSocketClientEndpoint = new CardlinkWebSocketClientEndpoint();
			client.connectToServer(cardlinkWebSocketClientEndpoint, new URI("wss://localhost:8443/websocket/null"));
			
			try {
				// give server a little bit of time to process
				Thread.sleep(100);

				EventServicePortType eventServicePortType = create(() -> new EventService().getEventServicePort(),"https://localhost:8443/services/EventService/v7.2");
				GetCards parameter = new GetCards();
				eventServicePortType.getCards(parameter).getCards().getCard().forEach(cardTypeType -> {
					System.out.println("Found card: " + cardTypeType.getCardHandle());
					assertEquals("537e7eb7-82cd-4af0-90f2-3e514109f542", cardTypeType.getCardHandle());
				});

				CardServicePortType cardService = create(() -> new CardService().getCardServicePort(),"https://localhost:8443/services/CardService/v8.2");

				var secureSendAPDU = new SecureSendAPDU();
				secureSendAPDU.setSignedScenario("eyJ0eXAiOiJKV1QiLCJ4NWMiOlsiTUlJRGF6Q0NBbE9nQXdJQkFnSUdBWUcwUWgrZU1BMEdDU3FHU0liM0RRRUJDd1VBTURZeEV6QVJCZ05WQkFNTUNrbHVZMlZ1ZEdWeVoza3hFakFRQmdOVkJBb01DV052Ym01bFkzUnZjakVMTUFrR0ExVUVCaE1DUkVVd0hoY05Nakl3TmpNd01UQTFOekl4V2hjTk1qY3dOakk1TVRBMU56SXhXakEyTVJNd0VRWURWUVFEREFwSmJtTmxiblJsY21kNU1SSXdFQVlEVlFRS0RBbGpiMjV1WldOMGIzSXhDekFKQmdOVkJBWVRBa1JGTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUFveTRXeDB2OThObTZzYTdMQ3Q0aldGd2sxVmMxN25vSXhFeHBWdmlXYkszVit1Rm5mb1Q0c1NxZXpFVFFrTUxCRDFtTjAwVWJrY0JWN0VENzFxQll3NjJQbjZ3cEdZZmZ4UWhnRURhOG9kc1B2TFM0ME9CYXI4MjRDRFBKWERCVHA5U2dLK0Rkc2JvTDd3UTg1VW9pRnZUOE1xekZYaUtqdERNZVRxcjFKMlB1emw1ZkdpbWtIQlBHa2NpOFhmYTFzcldyU0lFeDYvVUVyT2VLMlZLQ3FkRWhnWjNEMm1XWFc3K25BenBlYTVTeDJCeW5TeDdvZ0I3a1RURlZ5VEVGTlh2NW9qWlFlRGlCZ0dzNSs3S1dWOCtmdVhzTWRNTG1nempwb2hvcDhSQ1lkaVdSRUdkS2xFay9YbWVrRWpGRGE5Vm1RTkRjNDJ0dTE3YnhvMnJTd3dJREFRQUJvMzh3ZlRBZkJnTlZIU01FR0RBV2dCUTBnbitXRUVXMmpxdHU4WkZEcE1yTXkyWFIzVEFkQmdOVkhRNEVGZ1FVTklKL2xoQkZ0bzZyYnZHUlE2VEt6TXRsMGQwd0RBWURWUjBUQVFIL0JBSXdBREFPQmdOVkhROEJBZjhFQkFNQ0JlQXdIUVlEVlIwbEJCWXdGQVlJS3dZQkJRVUhBd0lHQ0NzR0FRVUZCd01CTUEwR0NTcUdTSWIzRFFFQkN3VUFBNElCQVFCMXV0eHgzcUp4RFBORWo5SG4rUitmOXdoK2JqRWd6VkE1Y0lYS0JjUER2cjRIMXVMNmtlWCt5dWdkT0p1Sko5T1lBcHB6SUQ3U1EzeXhQRkhucnhCNVRDU1oxd3ZxR2g0eFkxZVBhdXpXb1FjN0VRZi9RbFFET0NONmhOQUVac2JMaGhRWkRhOVRTYkIyMmh6Q1M0RkhtTUh6ZjJpeVoxSVZLV3NqcFdJTitxWHdxRTY0M3JFM3l6aFFCMzBmV0tPVzl5S0xwUElSWWR5a0tTTUZUdngwaUlEUjN2RzUwbk9OaC9yZk1kRGVRY05jNTFWQThZdEYxU1pYN2pUNFUzV2I3VVdZSWtRc3dHRVVWWHVZa3lVOXRaUVR3WlMrRE5jYTlIdHZyOGFBdkpmTEZUaTZOejBaNmMyTG9reVB0Vkg2MXZsQ1lQYkxsQ09La3hha1lyZVkiXSwic3RwbCI6Ik1JSURhekNDQWxPZ0F3SUJBZ0lHQVlHMFFoK2VNQTBHQ1NxR1NJYjNEUUVCQ3dVQU1EWXhFekFSQmdOVkJBTU1Da2x1WTJWdWRHVnlaM2t4RWpBUUJnTlZCQW9NQ1dOdmJtNWxZM1J2Y2pFTE1Ba0dBMVVFQmhNQ1JFVXdIaGNOTWpJd05qTXdNVEExTnpJeFdoY05NamN3TmpJNU1UQTFOekl4V2pBMk1STXdFUVlEVlFRRERBcEpibU5sYm5SbGNtZDVNUkl3RUFZRFZRUUtEQWxqYjI1dVpXTjBiM0l4Q3pBSkJnTlZCQVlUQWtSRk1JSUJJakFOQmdrcWhraUc5dzBCQVFFRkFBT0NBUThBTUlJQkNnS0NBUUVBb3k0V3gwdjk4Tm02c2E3TEN0NGpXRndrMVZjMTdub0l4RXhwVnZpV2JLM1YrdUZuZm9UNHNTcWV6RVRRa01MQkQxbU4wMFVia2NCVjdFRDcxcUJZdzYyUG42d3BHWWZmeFFoZ0VEYThvZHNQdkxTNDBPQmFyODI0Q0RQSlhEQlRwOVNnSytEZHNib0w3d1E4NVVvaUZ2VDhNcXpGWGlLanRETWVUcXIxSjJQdXpsNWZHaW1rSEJQR2tjaThYZmExc3JXclNJRXg2L1VFck9lSzJWS0NxZEVoZ1ozRDJtV1hXNytuQXpwZWE1U3gyQnluU3g3b2dCN2tUVEZWeVRFRk5YdjVvalpRZURpQmdHczUrN0tXVjgrZnVYc01kTUxtZ3pqcG9ob3A4UkNZZGlXUkVHZEtsRWsvWG1la0VqRkRhOVZtUU5EYzQydHUxN2J4bzJyU3d3SURBUUFCbzM4d2ZUQWZCZ05WSFNNRUdEQVdnQlEwZ24rV0VFVzJqcXR1OFpGRHBNck15MlhSM1RBZEJnTlZIUTRFRmdRVU5JSi9saEJGdG82cmJ2R1JRNlRLek10bDBkMHdEQVlEVlIwVEFRSC9CQUl3QURBT0JnTlZIUThCQWY4RUJBTUNCZUF3SFFZRFZSMGxCQll3RkFZSUt3WUJCUVVIQXdJR0NDc0dBUVVGQndNQk1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQjF1dHh4M3FKeERQTkVqOUhuK1IrZjl3aCtiakVnelZBNWNJWEtCY1BEdnI0SDF1TDZrZVgreXVnZE9KdUpKOU9ZQXBweklEN1NRM3l4UEZIbnJ4QjVUQ1NaMXd2cUdoNHhZMWVQYXV6V29RYzdFUWYvUWxRRE9DTjZoTkFFWnNiTGhoUVpEYTlUU2JCMjJoekNTNEZIbU1IemYyaXlaMUlWS1dzanBXSU4rcVh3cUU2NDNyRTN5emhRQjMwZldLT1c5eUtMcFBJUllkeWtLU01GVHZ4MGlJRFIzdkc1MG5PTmgvcmZNZERlUWNOYzUxVkE4WXRGMVNaWDdqVDRVM1diN1VXWUlrUXN3R0VVVlh1WWt5VTl0WlFUd1pTK0ROY2E5SHR2cjhhQXZKZkxGVGk2TnowWjZjMkxva3lQdFZINjF2bENZUGJMbENPS2t4YWtZcmVZIiwiYWxnIjoiUlMyNTYifQ.eyJtZXNzYWdlIjp7ImNsaWVudFNlc3Npb25JZCI6IjUzN2U3ZWI3LTgyY2QtNGFmMC05MGYyLTNlNTE0MTA5ZjU0MiIsInNlcXVlbmNlQ291bnRlciI6MSwidGltZVNwYW4iOjEwMDAsInR5cGUiOiJTdGFuZGFyZFNjZW5hcmlvIiwidmVyc2lvbiI6IjEuMC4wIiwic3RlcHMiOlt7ImV4cGVjdGVkU3RhdHVzV29yZHMiOlsiOTAwMCIsIjZmMDAiXSwiY29tbWFuZEFwZHUiOiIwMGE0MDQwYyJ9XX19.O4K-xwtt8DmQZOTgzsXZIy492QkmmlZE1jIpA6kosjt_ghqTw6dqHh7hlqcXYWDTaXAadaPva5tr-b4bq8OrypGPI0tBXHsKxWkLoz0dZso315EgEYUVYAkrJXhmK91vkUb2ByJ0EAwCU7gnbnRzicAljYhJ7k7lCHCh9yq5bR-evSyPkcOtR4doyz0NwPiQDNO4_UetfRI_8t_O9d96-nq8tSwXTjPgkVJYrFX9uhXedPSe2Wt48tGTjL2ES-00mryHxuVk89nQQ61V9XTYZXic8mOtM1Ljv3hiLvQhqMdJNZp2rlI-yaDujUQ1SpATL44aeA1EcK16uSWu_0jMBw");
				cardService.secureSendAPDU(secureSendAPDU);


				cardlinkWebSocketClientEndpoint.closed.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				System.err.println("Got interrupted: " + e.getMessage());
			} catch (FaultMessage e) {
				e.printStackTrace();
			} catch (de.gematik.ws.conn.cardservice.wsdl.v8_2.FaultMessage e) {
			}
			
			
		} catch (URISyntaxException | DeploymentException | IOException e) {
			System.err.println("Can't connect to card: " + e.getMessage());
		}
	}

	public static <T> T create(Supplier<T> portSupplier, String endpointUrl) {
			T port = portSupplier.get();
			// Endpoint überschreiben (z.B. pro Umgebung)
			var bp = (jakarta.xml.ws.BindingProvider) port;
			bp.getRequestContext().put(
				jakarta.xml.ws.BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
				endpointUrl
			);

	 		Client client = ClientProxy.getClient(port);
			HTTPConduit conduit = (HTTPConduit) client.getConduit();

			TLSClientParameters tlsParams = new TLSClientParameters();
			tlsParams.setDisableCNCheck(true); // Hostname ignorieren

			tlsParams.setTrustManagers(new TrustManager[]{
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] xcs, String s) {}
					@Override
					public void checkServerTrusted(X509Certificate[] xcs, String s) {}
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				}
			});
 
			conduit.setTlsClientParameters(tlsParams);
	
			return port;
		// } catch (MalformedURLException e) {
		//	throw new RuntimeException(e);
		// }
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
				System.err.println("SSL configuration failed: " + e.getMessage());
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
				System.err.println("Failed to configure SSLEngine to accept all certificates: " + e.getMessage());
			}
		}
	}

}