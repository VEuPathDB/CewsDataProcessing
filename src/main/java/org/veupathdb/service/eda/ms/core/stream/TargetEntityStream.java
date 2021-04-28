package org.veupathdb.service.eda.ms.core.stream;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;

public class TargetEntityStream extends RootEntityStream {

  private final List<String> _outputVars;
  private final Map<String, RootEntityStream> _ancestorStreams;

  public TargetEntityStream(EntityDef targetEntity,
      List<VariableDef> outputVars, ReferenceMetadata metadata,
      Map<String, StreamSpec> streamSpecs, Map<String, InputStream> dataStreams) {
    // target stream is a special root stream which excludes no children
    super(targetEntity, metadata, streamSpecs, dataStreams, Collections.emptyList());

    // header names for values we will return
    _outputVars = VariableDef.toDotNotation(outputVars);

    // build ancestor streams for any required ancestors
    _ancestorStreams = new LinkedHashMap<>();
    List<EntityDef> ancestors = metadata.getAncestors(targetEntity);
    for (int i = 0; i < ancestors.size(); i++) {
      EntityDef entity = ancestors.get(i);
      if (dataStreams.containsKey(entity.getId())) {
        List<String> descendantsToExclude = getSubtreeEntityIds(i == 0 ? targetEntity : ancestors.get(i - 1));
        _ancestorStreams.put(entity.getId(),
            new RootEntityStream(entity, metadata, streamSpecs, dataStreams, descendantsToExclude));

      }
    }
  }

  private List<String> getSubtreeEntityIds(EntityDef rootEntity) {
    List<String> subtreeIds = new ArrayList<>();
    subtreeIds.add(rootEntity.getId());
    subtreeIds.addAll(_metadata
        .getDescendants(rootEntity).stream()
        .map(EntityDef::getId)
        .collect(Collectors.toList()));
    return subtreeIds;
  }

  @Override
  public LinkedHashMap<String, String> next() {
    // RootEntityStream.next() will give us our own native vars plus any pulled from descendants
    LinkedHashMap<String,String> row = super.next();

    // supplement with vars from ancestors
    for (RootEntityStream ancestorStream : _ancestorStreams.values()) {
      String ancestorIdColName = ancestorStream.getEntityIdColName();
      Predicate<Map<String,String>> isMatch = r -> r.get(ancestorIdColName).equals(row.get(ancestorIdColName));
      Optional<LinkedHashMap<String,String>> ancestorRow = ancestorStream.getPreviousRowIf(isMatch);
      while (ancestorStream.hasNext() && ancestorRow.isEmpty()) {
        // this row is a member of a new ancestor of this entity; move to the next row
        ancestorStream.next(); // throws away the previous ancestor row
        ancestorRow = ancestorStream.getPreviousRowIf(isMatch);
      }
      if (ancestorRow.isEmpty()) {
        // Still empty and ancestor stream is exhausted.  We expect every target entity row to
        // have a matching row in each ancestor entity's stream.  Not having one is a fatal error.
        throw new RuntimeException("Ancestor stream '" + ancestorStream.getEntity().getId() +
            "' could not provide a row matching '" + ancestorIdColName + "' with value '" + row.get(ancestorIdColName) + "'.");
      }
      row.putAll(ancestorRow.get());
    }

    // return only requested vars and in the correct order
    LinkedHashMap<String,String> outputRow = new LinkedHashMap<>();
    for (String col : _outputVars) {
      outputRow.put(col, row.get(col));
    }
    return outputRow;
  }
}
