package org.veupathdb.service.eda.ms;

import java.util.List;
import java.util.Map.Entry;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.validation.ValidationBundle;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.lib.container.jaxrs.providers.RequestIdProvider;
import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.service.eda.generated.model.EntityTabularPostResponseStream;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.model.ServerError;
import org.veupathdb.service.eda.generated.model.ServerErrorImpl;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityError;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityError.ErrorsType;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityError.ErrorsType.ByKeyType;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityErrorImpl;
import org.veupathdb.service.eda.generated.resources.Query;
import org.veupathdb.service.eda.ms.core.MergeRequestProcessor;

public class Service implements Query {

  private static final Logger LOG = LogManager.getLogger(Service.class);

  @Context
  Request _request;

  @DisableJackson
  @Override
  public PostQueryResponse postQuery(MergedEntityTabularPostRequest request) {
    try {
      return PostQueryResponse.respond200WithTextPlain(
          new EntityTabularPostResponseStream(
              new MergeRequestProcessor(request)
                  .createMergedResponseSupplier()));
    }
    catch (ValidationException e) {
      LOG.error("Invalid request", e);
      throw new BadRequestException(e.toString());
    }
  }
}
