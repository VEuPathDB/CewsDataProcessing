package org.veupathdb.service.eda.ms.core.request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.EdaSubsettingClient;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.generated.model.APIStudyDetail;
import org.veupathdb.service.eda.generated.model.DerivedVariableBulkMetadataRequest;
import org.veupathdb.service.eda.generated.model.DerivedVariableSpec;
import org.veupathdb.service.eda.ms.Resources;
import org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariable;
import org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariableFactory;

import java.util.List;
import java.util.Map.Entry;

public class RequestResources {

  private static final Logger LOG = LogManager.getLogger(RequestResources.class);

  protected final EdaSubsettingClient _subsetSvc;
  protected final ReferenceMetadata _metadata;
  protected final List<DerivedVariableSpec> _derivedVariableSpecs;
  protected final DerivedVariableFactory _derivedVariableFactory;

  public RequestResources(DerivedVariableBulkMetadataRequest request, Entry<String, String> authHeader) throws ValidationException {

    LOG.info("Received merging post request: " + JsonUtil.serializeObject(request));

    // create subsetting service client
    _subsetSvc = new EdaSubsettingClient(Resources.SUBSETTING_SERVICE_URL, authHeader);

    // get raw metadata for requested study
    APIStudyDetail studyDetail = _subsetSvc.getStudy(request.getStudyId())
        .orElseThrow(() -> new ValidationException("No study found with ID " + request.getStudyId()));

    // create reference metadata using collected information
    _metadata = new ReferenceMetadata(studyDetail);
    _derivedVariableSpecs = request.getDerivedVariables();
    _derivedVariableFactory = new DerivedVariableFactory(_metadata, _derivedVariableSpecs);
    for (DerivedVariable derivedVar : _derivedVariableFactory.getAllDerivedVars()) {
      // this call lets the plugins do additional setup where they can assume depended var metadata is incorporated
      derivedVar.validateDependedVariables();
      // incorporate this derived variable
      _metadata.incorporateDerivedVariable(derivedVar);
    }
  }

  public ReferenceMetadata getMetadata() { return _metadata; }
  public List<DerivedVariableSpec> getDerivedVariableSpecs() { return _derivedVariableSpecs; }
  public DerivedVariableFactory getDerivedVariableFactory() { return _derivedVariableFactory; }

}
