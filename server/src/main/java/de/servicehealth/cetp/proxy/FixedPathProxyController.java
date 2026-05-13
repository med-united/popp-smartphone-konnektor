package de.servicehealth.cetp.proxy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/")
public class FixedPathProxyController {

  @Inject KonnektorProxyService proxyService;

  @POST
  @Path("CertificateService")
  public Uni<Response> certificateService(String body, @Context HttpHeaders headers) {
    return proxyService.forward("CertificateService", body, headers.getRequestHeaders());
  }

  @POST
  @Path("AuthSignatureService")
  public Uni<Response> authSignatureService(String body, @Context HttpHeaders headers) {
    return proxyService.forward("AuthSignatureService", body, headers.getRequestHeaders());
  }
}
