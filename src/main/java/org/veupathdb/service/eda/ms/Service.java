package org.veupathdb.service.eda.ms;

import java.util.List;
import java.util.Map.Entry;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.validation.ValidationBundle;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.lib.container.jaxrs.providers.RequestIdProvider;
import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.service.eda.ms.core.MergeRequestProcessor;
import org.veupathdb.service.eda.generated.model.EntityTabularPostResponseStream;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.model.ServerError;
import org.veupathdb.service.eda.generated.model.ServerErrorImpl;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityError;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityError.ErrorsType;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityError.ErrorsType.ByKeyType;
import org.veupathdb.service.eda.generated.model.UnprocessableEntityErrorImpl;
import org.veupathdb.service.eda.generated.resources.Query;

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
      LOG.error("Invalide request", e);
      return PostQueryResponse.respond422WithApplicationJson(toUnprocessableEntityError(e.getValidationBundle()));
    }
    catch (Exception e) {
      LOG.error("Could not execute query", e);
      return PostQueryResponse.respond500WithApplicationJson(toServerError(_request, e));
    }
  }

  private static ServerError toServerError(Request request, Exception e) {
    ServerError errorBundle = new ServerErrorImpl();
    errorBundle.setMessage(e.getMessage());
    errorBundle.setRequestId(RequestIdProvider.getRequestId(request));
    return errorBundle;
  }

  private static UnprocessableEntityError toUnprocessableEntityError(ValidationBundle validationBundle) {
    ByKeyType keyedMap = new UnprocessableEntityErrorImpl.ErrorsTypeImpl.ByKeyTypeImpl();
    for (Entry<String, List<String>> error : validationBundle.getKeyedErrors().entrySet()) {
      keyedMap.setAdditionalProperties(error.getKey(), error.getValue());
    }
    ErrorsType errors = new UnprocessableEntityErrorImpl.ErrorsTypeImpl();
    errors.setGeneral(validationBundle.getUnkeyedErrors());
    errors.setByKey(keyedMap);
    UnprocessableEntityError errorBundle = new UnprocessableEntityErrorImpl();
    errorBundle.setMessage("Request is invalid.");
    errorBundle.setErrors(errors);
    return errorBundle;
  }
}
