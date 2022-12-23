package org.veupathdb.service.eda.ms.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.ws.rs.BadRequestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.ListBuilder;
import org.gusdb.fgputil.client.ResponseFuture;
import org.gusdb.fgputil.functional.FunctionalInterfaces.ConsumerWithException;
import org.gusdb.fgputil.iterator.IteratorUtil;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.EdaComputeClient;
import org.veupathdb.service.eda.common.client.EdaComputeClient.ComputeRequestBody;
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

  public static final String COMPUTED_VAR_STREAM_NAME = "__COMPUTED_VAR_STREAM__";

  private final String _studyId;
  private final List<APIFilter> _filters;
  private final String _targetEntityId;
  private final List<DerivedVariableSpec> _derivedVariables;
  private final List<VariableSpec> _outputVarSpecs;
  private final Optional<ComputeInfo> _computeInfo;
  private final Entry<String, String> _authHeader;

  public MergeRequestProcessor(MergedEntityTabularPostRequest request, Entry<String, String> authHeader) {
    LOG.info("Received tabular post request: " + JsonUtil.serializeObject(request));
    _studyId = request.getStudyId();
    _filters = request.getFilters();
    _targetEntityId = request.getEntityId();
    _derivedVariables = request.getDerivedVariables();
    _outputVarSpecs = request.getOutputVariables();
    _authHeader = authHeader;
    _computeInfo = Optional.ofNullable(request.getComputeSpec())
        .map(spec -> new ComputeInfo(spec.getComputeName(),
        new ComputeRequestBody(_studyId, _filters, _derivedVariables, spec.getComputeConfig())));
  }

  public Consumer<OutputStream> createMergedResponseSupplier() throws ValidationException {

    // create subsetting and compute clients
    EdaSubsettingClient subsetSvc = new EdaSubsettingClient(Resources.SUBSETTING_SERVICE_URL, _authHeader);
    EdaComputeClient computeSvc = new EdaComputeClient(Resources.COMPUTE_SERVICE_URL, _authHeader);

    // build metadata for requested study
    APIStudyDetail studyDetail = subsetSvc.getStudy(_studyId)
        .orElseThrow(() -> new ValidationException("No study found with ID " + _studyId));

    // if compute specified, check if compute results are available; throw if not, get computed metadata if so
    _computeInfo.ifPresent(info -> {
      if (!computeSvc.isJobResultsAvailable(info.getComputeName(), info.getRequestBody()))
        throw new BadRequestException("Compute results are not available for the requested job.");
      else
        info.setMetadata(computeSvc.getJobVariableMetadata(info.getComputeName(), info.getRequestBody()));
    });


    // create reference metadata using collected information
    ReferenceMetadata metadata = new ReferenceMetadata(studyDetail,
        _computeInfo.map(ComputeInfo::getVariables).orElse(Collections.emptyList()), _derivedVariables);

    // validation of incoming request
    //  (validation based specifically on the requested entity done during spec creation)
    validateIncomingRequest(_targetEntityId, _outputVarSpecs, metadata, _computeInfo);

    // request validated; convert requested entity and vars to defs
    EntityDef targetEntity = metadata.getEntity(_targetEntityId).orElseThrow();
    List<VariableDef> outputVarDefs = metadata.getTabularColumns(targetEntity, _outputVarSpecs);
    List<VariableSpec> outputVars = new ArrayList<>(outputVarDefs.stream().map(v -> (VariableSpec)v).toList());

    // build specs for streams to be merged into this request's response
    Optional<EntityDef> computedEntity = _computeInfo
        .map(info -> metadata.getEntity(info.getComputeEntity()).orElseThrow());
    Map<String, StreamSpec> requiredStreams = new SubsettingStreamSpecFactory(metadata, targetEntity, computedEntity, outputVarDefs).createSpecs();

    // if computed vars present, add stream spec for compute and add computed vars to output columns
    addComputedDataSpecs(_computeInfo.map(ComputeInfo::getVariables).orElse(Collections.emptyList()), requiredStreams, outputVars);

    // create stream generator
    Function<StreamSpec, ResponseFuture> streamGenerator = spec ->
        COMPUTED_VAR_STREAM_NAME.equals(spec.getStreamName())
        // need to get compute stream from compute service
        ? computeSvc.getJobTabularOutput(_computeInfo.get().getComputeName(), _computeInfo.get().getRequestBody())
        // all other streams come from subsetting service
        : subsetSvc.getTabularDataStream(metadata, _filters, Optional.empty(), spec);

    return out -> {

      // create stream processor
      ConsumerWithException<Map<String,InputStream>> streamProcessor = dataStreams ->
          writeMergedStream(metadata, targetEntity, computedEntity, outputVars, requiredStreams, dataStreams, out);

      // build and process streams
      StreamingDataClient.buildAndProcessStreams(new ArrayList<>(requiredStreams.values()), streamGenerator, streamProcessor);
    };
  }

  private void addComputedDataSpecs(
      List<VariableMapping> varMappings,
      Map<String, StreamSpec> requiredStreams,
      List<VariableSpec> outputVars) {

    // if no computed vars present, nothing to do
    if (varMappings.isEmpty()) return;

    // create variable specs from computed var metadata
    List<VariableSpec> computedVars = new ArrayList<>();
    varMappings.forEach(varMapping -> {
      if (varMapping.getIsCollection()) {
        // for collection vars, expect columns for each member
        computedVars.addAll(varMapping.getMembers());
      }
      else {
        // for non-collections, add the mapping's spec
        computedVars.add(varMapping.getVariableSpec());
      }
    });

    // use computed var specs to create a stream spec and add them to output vars
    requiredStreams.put(COMPUTED_VAR_STREAM_NAME, new StreamSpec(COMPUTED_VAR_STREAM_NAME,
        varMappings.get(0).getVariableSpec().getEntityId()).addVars(computedVars));
    outputVars.addAll(computedVars);

  }

  private static void validateIncomingRequest(
      String targetEntityId,
      List<VariableSpec> outputVars,
      ReferenceMetadata metadata,
      Optional<ComputeInfo> computeInfo) throws ValidationException {
    StreamSpec requestSpec = new StreamSpec("incoming", targetEntityId);
    requestSpec.addAll(outputVars);
    new EdaMergingSpecValidator()
      .validateStreamSpecs(ListBuilder.asList(requestSpec), metadata)
      .throwIfInvalid();

    // no need to check compute if it doesn't exist
    if (computeInfo.isPresent()) {
      // compute present; make sure computed var entity is the same as, or an ancestor of, the target entity (needed for now)
      Predicate<String> isComputeVarEntity = entityId -> entityId.equals(computeInfo.get().getComputeEntity());
      if (!isComputeVarEntity.test(targetEntityId) && metadata
          .getAncestors(metadata.getEntity(targetEntityId).orElseThrow()).stream()
          .filter(entity -> isComputeVarEntity.test(entity.getId()))
          .findFirst().isEmpty()) {
        throw new ValidationException("Entity of computed variable must be the same as, or ancestor of, the target entity");
      }
    }
  }

  private static void writeMergedStream(ReferenceMetadata metadata, EntityDef targetEntity,
      Optional<EntityDef> computedEntity, List<VariableSpec> outputVars, Map<String, StreamSpec> requiredStreams,
      Map<String, InputStream> dataStreams, OutputStream out) {

    LOG.info("All requested streams (" + requiredStreams.size() + ") ready for consumption");

    EntityStream targetEntityStream =
        requiredStreams.size() == 1 && metadata.getDerivedVariableSpecs().isEmpty() ?
        // speed optimized by directly copying a single stream's result into the output
        new EntityStream(requiredStreams.values().iterator().next(), dataStreams.values().iterator().next(), metadata) :
        // more than one stream requires merge logic
        new TargetEntityStream(targetEntity, computedEntity, outputVars, metadata, requiredStreams, dataStreams);

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
