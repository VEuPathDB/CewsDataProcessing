package org.veupathdb.service.eda.ms.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.gusdb.fgputil.ListBuilder;
import org.gusdb.fgputil.functional.FunctionalInterfaces.ConsumerWithException;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.ClientUtil;
import org.veupathdb.service.eda.common.client.EdaMergingClient;
import org.veupathdb.service.eda.common.client.EdaSubsettingClient;
import org.veupathdb.service.eda.common.client.StreamSpec;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.generated.model.APIFilter;
import org.veupathdb.service.eda.generated.model.APIStudyDetail;
import org.veupathdb.service.eda.generated.model.DerivedVariable;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.model.VariableSpec;
import org.veupathdb.service.eda.ms.Resources;

public class MergeRequestProcessor {

  private final String _studyId;
  private final List<APIFilter> _filters;
  private final String _targetEntityId;
  private final List<DerivedVariable> _derivedVariables;
  private final List<VariableSpec> _outputVars;

  public MergeRequestProcessor(MergedEntityTabularPostRequest request) {
    _studyId = request.getStudyId();
    _filters = request.getFilters();
    _targetEntityId = request.getEntityId();
    _derivedVariables = request.getDerivedVariables();
    _outputVars = request.getOutputVariableIds();
  }

  public Consumer<OutputStream> createMergedResponseSupplier() throws ValidationException {

    // create subsetting client
    EdaSubsettingClient subsetSvc = new EdaSubsettingClient(Resources.SUBSETTING_SERVICE_URL);

    // build metadata for requested study
    APIStudyDetail studyDetail = subsetSvc.getStudy(_studyId)
        .orElseThrow(() -> new ValidationException("No study found with ID " + _studyId));
    ReferenceMetadata metadata = new ReferenceMetadata(studyDetail, _derivedVariables);

    // validation of incoming request
    //  (validation based specifically on the requested entity done during spec creation)
    validateIncomingRequest(_targetEntityId, _outputVars, metadata);

    // build specs for streams to be merged into this request's response
    List<StreamSpec> requiredStreams = new SubsettingStreamSpecFactory(
        metadata, _targetEntityId, _outputVars).createSpecs();

    // create stream generator
    Function<StreamSpec,InputStream> streamGenerator = spec -> subsetSvc
        .getTabularDataStream(_studyId, _filters, spec);

    return out -> {

      // create stream processor
      ConsumerWithException<Map<String,InputStream>> streamProcessor = dataStreams ->
          StreamMerger.writeMergedStream(metadata, _targetEntityId,
              _outputVars, requiredStreams, dataStreams, out);

      // build and process streams
      ClientUtil.buildAndProcessStreams(requiredStreams, streamGenerator, streamProcessor);
    };
  }

  private static void validateIncomingRequest(String targetEntityId,
      List<VariableSpec> outputVars, ReferenceMetadata metadata) throws ValidationException {
    EdaMergingClient mergeClient = new EdaMergingClient(null);
    StreamSpec requestSpec = new StreamSpec("incoming", targetEntityId);
    requestSpec.addAll(outputVars);
    mergeClient.validateStreamSpecs(ListBuilder.asList(requestSpec), metadata).throwIfInvalid();
  }

}
