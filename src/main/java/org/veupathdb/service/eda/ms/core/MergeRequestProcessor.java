package org.veupathdb.service.eda.ms.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.ListBuilder;
import org.gusdb.fgputil.client.ResponseFuture;
import org.gusdb.fgputil.functional.FunctionalInterfaces.ConsumerWithException;
import org.gusdb.fgputil.iterator.IteratorUtil;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.EdaComputeClient;
import org.veupathdb.service.eda.common.client.EdaSubsettingClient;
import org.veupathdb.service.eda.common.client.StreamingDataClient;
import org.veupathdb.service.eda.common.client.spec.EdaMergingSpecValidator;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.*;
import org.veupathdb.service.eda.ms.Resources;
import org.veupathdb.service.eda.ms.core.stream.EntityStream;
import org.veupathdb.service.eda.ms.core.stream.TargetEntityStream;

import static org.gusdb.fgputil.FormatUtil.TAB;

public class MergeRequestProcessor {

  private static final Logger LOG = LogManager.getLogger(MergeRequestProcessor.class);

  private final String _studyId;
  private final List<APIFilter> _filters;
  private final String _targetEntityId;
  private final List<DerivedVariable> _derivedVariables;
  private final List<VariableSpec> _outputVarSpecs;
  private final ComputeSpecForMerging _computeRequestSpec;
  private final Entry<String, String> _authHeader;

  public MergeRequestProcessor(MergedEntityTabularPostRequest request, Entry<String, String> authHeader) {
    LOG.info("Received tabular post request: " + JsonUtil.serializeObject(request));
    _studyId = request.getStudyId();
    _filters = request.getFilters();
    _targetEntityId = request.getEntityId();
    _derivedVariables = request.getDerivedVariables();
    _outputVarSpecs = request.getOutputVariables();
    _computeRequestSpec = request.getComputeSpec();
    _authHeader = authHeader;
  }

  public Consumer<OutputStream> createMergedResponseSupplier() throws ValidationException {

    // create subsetting client
    EdaSubsettingClient subsetSvc = new EdaSubsettingClient(Resources.SUBSETTING_SERVICE_URL, _authHeader);
    EdaComputeClient computeSvc = new EdaComputeClient(Resources.COMPUTE_SERVICE_URL, _authHeader);

    // build metadata for requested study
    APIStudyDetail studyDetail = subsetSvc.getStudy(_studyId)
        .orElseThrow(() -> new ValidationException("No study found with ID " + _studyId));
    ReferenceMetadata metadata = new ReferenceMetadata(studyDetail, _derivedVariables);

    // validation of incoming request
    //  (validation based specifically on the requested entity done during spec creation)
    validateIncomingRequest(_targetEntityId, _outputVarSpecs, metadata);

    // request validated; convert requested entity and vars to defs
    EntityDef targetEntity = metadata.getEntity(_targetEntityId).orElseThrow();
    List<VariableDef> outputVars = metadata.getTabularColumns(targetEntity, _outputVarSpecs);

    // build specs for streams to be merged into this request's response
    Map<String, StreamSpec> requiredStreams = new SubsettingStreamSpecFactory(metadata, targetEntity, outputVars).createSpecs();
    List<StreamSpec> requiredStreamList = new ArrayList<>(requiredStreams.values());

    // create stream generator
    Function<StreamSpec, ResponseFuture> streamGenerator = spec -> subsetSvc
        .getTabularDataStream(metadata, _filters, Optional.empty(), spec);

    return out -> {

      // create stream processor
      ConsumerWithException<Map<String,InputStream>> streamProcessor = dataStreams ->
          writeMergedStream(metadata, targetEntity,
              outputVars, requiredStreams, dataStreams, out);

      // build and process streams
      StreamingDataClient.buildAndProcessStreams(requiredStreamList, streamGenerator, streamProcessor);
    };
  }

  private static void validateIncomingRequest(String targetEntityId,
      List<VariableSpec> outputVars, ReferenceMetadata metadata) throws ValidationException {
    StreamSpec requestSpec = new StreamSpec("incoming", targetEntityId);
    requestSpec.addAll(outputVars);
    new EdaMergingSpecValidator()
      .validateStreamSpecs(ListBuilder.asList(requestSpec), metadata)
      .throwIfInvalid();
  }

  private static void writeMergedStream(ReferenceMetadata metadata, EntityDef targetEntity,
      List<VariableDef> outputVars, Map<String, StreamSpec> requiredStreams,
      Map<String, InputStream> dataStreams, OutputStream out) {

    LOG.info("All requested streams (" + requiredStreams.size() + ") ready for consumption");

    EntityStream targetEntityStream =
        requiredStreams.size() == 1 && metadata.getDerivedVariables().isEmpty() ?
        // speed optimized by directly copying a single stream's result into the output
        new EntityStream(requiredStreams.values().iterator().next(), dataStreams.values().iterator().next(), metadata) :
        // more then one stream requires merge logic
        new TargetEntityStream(targetEntity, outputVars, metadata, requiredStreams, dataStreams);

    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out))) {

      // write the header row
      String headerRow = String.join(TAB, VariableDef.toDotNotation(outputVars));
      LOG.info("Writing header row:" + headerRow);
      writer.write(headerRow);
      writer.newLine();

      // write the entity rows
      for (Map<String,String> row : IteratorUtil.toIterable(targetEntityStream)) {
        writer.write(String.join(TAB, row.values()));
        writer.newLine();
      }

      // flush any remaining chars
      writer.flush();
    }
    catch (IOException e) {
      throw new RuntimeException("Unable to write output stream", e);
    }
  }
}
