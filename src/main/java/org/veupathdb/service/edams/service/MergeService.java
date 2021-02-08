package org.veupathdb.service.edams.service;

import org.veupathdb.lib.container.jaxrs.server.annotations.DisableJackson;
import org.veupathdb.service.edams.generated.model.EntityTabularPostResponseStream;
import org.veupathdb.service.edams.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.edams.generated.resources.Query;

public class MergeService implements Query {

  @DisableJackson
  @Override
  public PostQueryResponse postQuery(MergedEntityTabularPostRequest entity) {
    return PostQueryResponse.respond200WithTextPlain(new EntityTabularPostResponseStream(outStream -> {}));
  }
}
