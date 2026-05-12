package de.servicehealth.cetp.proxy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/konnektor-proxy")
public class KonnektorProxyController {

  @Inject KonnektorProxyService proxyService;

  @POST
  @Path("{path: .*}")
  public Uni<Response> proxy(
      @PathParam("path") String path, String body, @Context HttpHeaders headers) {
    return proxyService.forward(path, body, headers.getRequestHeaders());
  }
}
