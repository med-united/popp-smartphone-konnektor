package de.servicehealth.cetp.proxy;

import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class KonnektorProxyService {

  @Inject Vertx vertx;

  @Inject KonnektorProxyConfig config;

  @ConfigProperty(name = "popp.real-connector-url")
  String connectorUrl;

  WebClient webClient;

  private static final Logger LOG = Logger.getLogger(KonnektorProxyService.class.getName());

  @PostConstruct
  void init() {
    URI uri = URI.create(connectorUrl);
    boolean ssl = "https".equalsIgnoreCase(uri.getScheme());

    WebClientOptions options =
        new WebClientOptions()
            .setSsl(ssl)
            .setTrustAll(config.trustAllHosts())
            .setVerifyHost(!config.trustAllHosts())
            .setDefaultHost(uri.getHost())
            .setDefaultPort(443)
            .setKeyCertOptions(
                new PfxOptions()
                    .setPath(config.keystorePath())
                    .setPassword(config.keystorePassword()));

    webClient = WebClient.create(vertx.getDelegate(), options);
  }

  public Uni<Response> forward(
      String wsPath, String body, MultivaluedMap<String, String> requestHeaders) {
    HttpRequest<Buffer> req = webClient.post("/ws/" + wsPath);
    requestHeaders.forEach(
        (name, values) -> {
          if (!name.equalsIgnoreCase("host")) {
            values.forEach(v -> req.putHeader(name, v));
          }
        });
    return Uni.createFrom()
        .completionStage(
            req.sendBuffer(Buffer.buffer(body != null ? body : "")).toCompletionStage())
        .map(
            resp -> {
              Response.ResponseBuilder rb = Response.status(resp.statusCode());
              String ct = resp.getHeader("Content-Type");
              if (ct != null) {
                rb.header("Content-Type", ct);
              }
              LOG.fine("CODE: " + resp.statusCode() + " RESPONSE: " + resp.bodyAsString());
              return rb.entity(resp.bodyAsString()).build();
            });
  }
}
