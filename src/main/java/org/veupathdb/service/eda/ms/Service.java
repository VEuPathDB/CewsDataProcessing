package org.veupathdb.service.eda.ms;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.server.ContainerRequest;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.lib.container.jaxrs.providers.UserProvider;
import org.veupathdb.lib.container.jaxrs.server.annotations.Authenticated;
import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.lib.container.jaxrs.utils.RequestKeys;
import org.veupathdb.service.eda.common.auth.StudyAccess;
import org.veupathdb.service.eda.generated.model.EntityTabularPostResponseStream;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.resources.Query;
import org.veupathdb.service.eda.ms.core.MergeRequestProcessor;

import java.util.Map.Entry;

@Authenticated(allowGuests = true)
public class Service implements Query {

  private static final Logger LOG = LogManager.getLogger(Service.class);

  private static final String MISSING_AUTH_MSG =
      "Request must include authentication information in the form of a " +
      RequestKeys.AUTH_HEADER + " header or query param";

  @Context
  ContainerRequest _request;

  @DisableJackson
  @Override
  public PostQueryResponse postQuery(MergedEntityTabularPostRequest requestBody) {
    // no need to check perms; only internal clients can access this endpoint
    return PostQueryResponse.respond200WithTextTabSeparatedValues(processRequest(_request, requestBody, false));
  }

  static EntityTabularPostResponseStream processRequest(ContainerRequest request, MergedEntityTabularPostRequest requestBody, boolean checkPermissions) {
    try {
      Entry<String,String> authHeader = UserProvider.getSubmittedAuth(request)
          .orElseThrow(() -> new BadRequestException(MISSING_AUTH_MSG));
      if (checkPermissions) {
        StudyAccess.confirmPermission(authHeader, Resources.DATASET_ACCESS_SERVICE_URL,
            requestBody.getStudyId(), StudyAccess::allowResultsAll);
      }
      return new EntityTabularPostResponseStream(
              new MergeRequestProcessor(requestBody, authHeader)
                  .createMergedResponseSupplier());
    }
    catch (ValidationException e) {
      LOG.error("Invalid request", e);
      throw new BadRequestException(e.toString());
    }
  }
}
