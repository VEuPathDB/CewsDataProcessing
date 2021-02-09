package org.veupathdb.service.edams.core;

import java.io.OutputStream;
import java.util.function.Consumer;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.edams.generated.model.MergedEntityTabularPostRequest;

public class StreamMerger {

  public static Consumer<OutputStream> createMergedResponseSupplier(
      MergedEntityTabularPostRequest request,
      String subsettingServiceUrl) throws ValidationException {
    return stream -> {};
  }

}
