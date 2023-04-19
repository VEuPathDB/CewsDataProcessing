package org.veupathdb.service.eda.ms.core.request;

import jakarta.ws.rs.BadRequestException;
import org.gusdb.fgputil.client.ResponseFuture;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.EdaComputeClient;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.generated.model.APIFilter;
import org.veupathdb.service.eda.generated.model.MergedEntityTabularPostRequest;
import org.veupathdb.service.eda.generated.model.VariableSpec;
import org.veupathdb.service.eda.ms.Resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

public class MergedTabularRequestResources extends RequestResources {

  private final EdaComputeClient _computeSvc;
  private final List<APIFilter> _subsetFilters;
  private final Optional<ComputeInfo> _computeInfo;
  private final String _targetEntityId;
  private final List<VariableSpec> _outputVarSpecs;

  public MergedTabularRequestResources(MergedEntityTabularPostRequest request, Entry<String, String> authHeader) throws ValidationException {
    super(request, authHeader);
    _targetEntityId = request.getEntityId();
    _outputVarSpecs = request.getOutputVariables();
    _computeSvc = new EdaComputeClient(Resources.COMPUTE_SERVICE_URL, authHeader);
    _subsetFilters = Optional.ofNullable(request.getFilters()).orElse(Collections.emptyList());
    _computeInfo = Optional.ofNullable(request.getComputeSpec())
        .map(spec -> new ComputeInfo(spec.getComputeName(),
            new EdaComputeClient.ComputeRequestBody(_metadata.getStudyId(), _subsetFilters, _derivedVariableSpecs, spec.getComputeConfig())));
    incorporateCompute();
  }

  private void incorporateCompute() {
    // if compute specified, check if compute results are available; throw if not, get computed metadata if so
    _computeInfo.ifPresent(info -> {
      if (!_computeSvc.isJobResultsAvailable(info.getComputeName(), info.getRequestBody()))
        throw new BadRequestException("Compute results are not available for the requested job.");
      else
        info.setMetadata(_computeSvc.getJobVariableMetadata(info.getComputeName(), info.getRequestBody()));
    });
    _computeInfo.ifPresent(computeInfo -> _metadata.incorporateComputedVariables(computeInfo.getVariables()));
  }

  public ResponseFuture getSubsettingTabularStream(StreamSpec spec) {

    // for derived var plugins, need to ensure filters overrides produce set of rows which are a subset of the rows
    //   produced by the "global" filters.  Easiest way to do that is to simply combine the filters, resulting in
    //   an intersection of the global subset and the overridden subset
    if (spec.getFiltersOverride().isPresent() && !_subsetFilters.isEmpty()) {
      StreamSpec modifiedSpec = new StreamSpec(spec.getStreamName(), spec.getEntityId());
      modifiedSpec.addAll(spec);
      List<APIFilter> combinedFilters = new ArrayList<>();
      combinedFilters.addAll(_subsetFilters);
      combinedFilters.addAll(spec.getFiltersOverride().get());
      modifiedSpec.setFiltersOverride(combinedFilters);
      spec = modifiedSpec;
    }

    return _subsetSvc.getTabularDataStream(_metadata, _subsetFilters, spec);
  }

  public ResponseFuture getComputeTabularStream() {
    return _computeSvc.getJobTabularOutput(_computeInfo.orElseThrow().getComputeName(), _computeInfo.get().getRequestBody());
  }

  public List<APIFilter> getSubsetFilters() { return _subsetFilters; }
  public Optional<ComputeInfo> getComputeInfo() { return _computeInfo; }
  public String getTargetEntityId() { return _targetEntityId; }
  public List<VariableSpec> getOutputVariableSpecs() { return _outputVarSpecs; }

}
