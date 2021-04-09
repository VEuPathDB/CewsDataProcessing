package org.veupathdb.service.eda.ms.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.VariableSpec;

public class SubsettingStreamSpecFactory {

  private final ReferenceMetadata _metadata;
  private final String _targetEntityId;
  private final List<VariableSpec> _outputVars;

  public SubsettingStreamSpecFactory(
      ReferenceMetadata metadata, String targetEntityId, List<VariableSpec> outputVars) {
    _metadata = metadata;
    _targetEntityId = targetEntityId;
    _outputVars = outputVars;
  }
  public List<StreamSpec> createSpecs() throws ValidationException {

    // gather all needed vars and sort by entity
    Map<String,List<VariableDef>> sortedVars =
      findAllNeededVars(_outputVars, new ArrayList<>())
        .stream().collect(Collectors.groupingBy(VariableDef::getEntityId));

    // FIXME: remove once >1 streams and transforms supported
    if (sortedVars.size() > 1 || !_metadata.getDerivedVariables().isEmpty()) {
      throw new ValidationException("Requests requiring derived or inherited vars are not yet supported.");
    }

    // convert sorted vars to stream specs
    return sortedVars.entrySet().stream()
      .map(entry -> new StreamSpec(entry.getKey(), entry.getKey())
        .addVars(entry.getValue()))
      .collect(Collectors.toList());
  }

  private List<VariableDef> findAllNeededVars(List<VariableSpec> neededVars, ArrayList<VariableDef> accumulator) {
    for (VariableSpec varSpec : neededVars) {
      VariableDef var = _metadata.getVariableDef(varSpec);
      switch(var.getSource()) {
        case NATIVE:
        case INHERITED:
          accumulator.add(var);
          break;
        case DERIVED_BY_REDUCTION:
        case DERIVED_BY_TRANSFORM:
          findAllNeededVars(_metadata
              .findDerivedVariable(var)
              .orElseThrow()
              .getDependentVariables(), accumulator);
          break;
      }
    }
    return accumulator;
  }
}
