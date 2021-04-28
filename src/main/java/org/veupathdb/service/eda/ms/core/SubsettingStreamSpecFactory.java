package org.veupathdb.service.eda.ms.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;

import static org.gusdb.fgputil.functional.Functions.newLinkedHashMapCollector;

public class SubsettingStreamSpecFactory {

  private final ReferenceMetadata _metadata;
  private final List<VariableDef> _outputVars;

  public SubsettingStreamSpecFactory(ReferenceMetadata metadata, List<VariableDef> outputVars) {
    _metadata = metadata;
    _outputVars = outputVars;
  }

  public Map<String, StreamSpec> createSpecs() throws ValidationException {

    // gather all needed vars and sort by entity
    Map<String,List<VariableDef>> sortedVars =
      findAllNeededVars(_outputVars, new ArrayList<>())
        .stream().collect(Collectors.groupingBy(VariableDef::getEntityId));

    // convert sorted vars to stream specs
    return sortedVars.entrySet().stream()
      // important: for the purposes of the merging service the stream name must be the entity ID;
      //     this ensures uniqueness of entities (one stream per entity) and easy lookup by entity ID
      .map(entry -> new StreamSpec(entry.getKey(), entry.getKey())
        .addVars(entry.getValue()))
      .collect(newLinkedHashMapCollector(StreamSpec::getStreamName));
  }

  private List<VariableDef> findAllNeededVars(List<VariableDef> neededVars, ArrayList<VariableDef> accumulator) {
    for (VariableDef var : neededVars) {
      switch(var.getSource()) {
        case ID: // skip IDs; we get them for free
          break;
        case NATIVE:
        case INHERITED:
          accumulator.add(var);
          break;
        case DERIVED_BY_REDUCTION:
        case DERIVED_BY_TRANSFORM:
          findAllNeededVars(_metadata
              .findDerivedVariable(var)
              .orElseThrow()
              .getInputVars()
              .stream()
              .map(spec -> _metadata.getVariable(spec).orElseThrow())
              .collect(Collectors.toList()),
            accumulator);
          break;
      }
    }
    return accumulator;
  }
}
