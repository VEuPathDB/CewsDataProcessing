package org.veupathdb.service.edams.core;

import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.edams.generated.model.DerivedVariable;
import org.veupathdb.service.edams.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.edams.generated.model.VariableSpec;

public class StreamMerger {

  private final String _studyId;
  private final String _targetEntityId;

  private final List<DerivedVariable> _derivedVariables;
  private final List<VariableSpec> _outputVars;
  private final SubsettingRequestFactory _subsettingRequestFactory;

  public StreamMerger(
      MergedEntityTabularPostRequest request,
      String subsettingServiceUrl) {
    _studyId = request.getStudyId();
    _targetEntityId = request.getEntityId();
    _derivedVariables = request.getDerivedVariables();
    _outputVars = request.getOutputVariableIds();
    _subsettingRequestFactory = new SubsettingRequestFactory(subsettingServiceUrl, request.getFilters());
  }
  public static Consumer<OutputStream> createMergedResponseSupplier() throws ValidationException {


    return stream -> {};
  }

}
