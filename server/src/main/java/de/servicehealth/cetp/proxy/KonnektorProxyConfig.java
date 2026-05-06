package de.servicehealth.cetp.proxy;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "konnektor-proxy")
public interface KonnektorProxyConfig {

  @WithName("trust-all-hosts")
  boolean trustAllHosts();

  @WithName("keystore-path")
  String keystorePath();

  @WithName("keystore-password")
  String keystorePassword();
}
