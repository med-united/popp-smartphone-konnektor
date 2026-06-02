package de.servicehealth.popp.ssl;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.quarkus.vertx.http.HttpServerOptionsCustomizer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.TrustOptions;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

@ApplicationScoped
public class TrustAllMtlsCustomizer implements HttpServerOptionsCustomizer {

  @Override
  public void customizeHttpsServer(HttpServerOptions options) {
    options.setTrustOptions(
        new TrustOptions() {
          @Override
          public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
            return InsecureTrustManagerFactory.INSTANCE;
          }

          @Override
          public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
            return null;
          }

          @Override
          public TrustOptions copy() {
            return this;
          }
        });
  }
}
