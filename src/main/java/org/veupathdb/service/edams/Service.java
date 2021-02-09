package org.veupathdb.service.edams;

import java.util.List;
import java.util.Map.Entry;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import org.gusdb.fgputil.validation.ValidationBundle;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.lib.container.jaxrs.providers.RequestIdProvider;
import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.service.edams.core.StreamMerger;
import org.veupathdb.service.edams.generated.model.EntityTabularPostResponseStream;
import org.veupathdb.service.edams.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.edams.generated.model.ServerError;
import org.veupathdb.service.edams.generated.model.ServerErrorImpl;
import org.veupathdb.service.edams.generated.model.UnprocessableEntityError;
import org.veupathdb.service.edams.generated.model.UnprocessableEntityError.ErrorsType;
import org.veupathdb.service.edams.generated.model.UnprocessableEntityError.ErrorsType.ByKeyType;
import org.veupathdb.service.edams.generated.model.UnprocessableEntityErrorImpl;
import org.veupathdb.service.edams.generated.resources.Query;

public class Service implements Query {

  @Context
  Request _request;

  @DisableJackson
  @Override
  public PostQueryResponse postQuery(MergedEntityTabularPostRequest request) {
    try {
      return PostQueryResponse.respond200WithTextPlain(
          new EntityTabularPostResponseStream(
              StreamMerger.createMergedResponseSupplier(request, Resources.SUBSETTING_SERVICE_URL)));
    }
    catch (ValidationException e) {
      return PostQueryResponse.respond422WithApplicationJson(toUnprocessableEntityError(e.getValidationBundle()));
    }
    catch (Exception e) {
      return PostQueryResponse.respond500WithApplicationJson(toServerError(e));
    }
  }

  private ServerError toServerError(Exception e) {
    ServerError errorBundle = new ServerErrorImpl();
    errorBundle.setMessage(e.getMessage());
    errorBundle.setRequestId(RequestIdProvider.getRequestId(_request));
    return errorBundle;
  }

  private UnprocessableEntityError toUnprocessableEntityError(ValidationBundle validationBundle) {
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
