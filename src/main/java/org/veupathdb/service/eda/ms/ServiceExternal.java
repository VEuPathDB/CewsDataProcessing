package org.veupathdb.service.eda.ms;

import jakarta.ws.rs.core.Context;
import org.glassfish.jersey.server.ContainerRequest;
import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.resources.MergingQueryExternal;

public class ServiceExternal implements MergingQueryExternal {

  @Context
  ContainerRequest _request;

  @DisableJackson
  @Override
  public PostMergingQueryExternalResponse postMergingQueryExternal(MergedEntityTabularPostRequest requestBody) {
    // check access to full tabular results since this endpoint is intended to be exposed through traefik
    return PostMergingQueryExternalResponse.respond200WithTextTabSeparatedValues(Service.processRequest(_request, requestBody, true));
  }

}
