package org.veupathdb.service.eda.ms;

import java.util.Map.Entry;
import java.util.Objects;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.server.ContainerRequest;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.lib.container.jaxrs.providers.UserProvider;
import org.veupathdb.lib.container.jaxrs.server.annotations.Authenticated;
import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.service.eda.generated.model.EntityTabularPostResponseStream;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.resources.Query;
import org.veupathdb.service.eda.ms.core.MergeRequestProcessor;

@Authenticated(allowGuests = true)
public class Service implements Query {

  private static final Logger LOG = LogManager.getLogger(Service.class);

  @Context
  ContainerRequest _request;

  @DisableJackson
  @Override
  public PostQueryResponse postQuery(MergedEntityTabularPostRequest requestBody) {
    try {
      // no need to check perms; only internal clients can access this endpoint
      Entry<String,String> authHeader = UserProvider
          .getSubmittedAuth(_request).orElseThrow();
      return PostQueryResponse.respond200WithTextTabSeparatedValues(
          new EntityTabularPostResponseStream(
              new MergeRequestProcessor(requestBody, authHeader)
                  .createMergedResponseSupplier()));
    }
    catch (ValidationException e) {
      LOG.error("Invalid request", e);
      throw new BadRequestException(e.toString());
    }
  }
}
