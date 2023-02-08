package org.veupathdb.service.eda.ms.core.stream;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.collection.InitialSizeStringMap;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.VariableSpec;

public class TargetEntityStream extends RootEntityStream {
  private static final Logger LOG = LogManager.getLogger(TargetEntityStream.class);

  private final String[] _outputVars;
  private final Map<String, RootEntityStream> _ancestorStreams;
  private final InitialSizeStringMap _outputRow;

  public TargetEntityStream(EntityDef targetEntity, Optional<EntityDef> computedEntity,
                            List<VariableSpec> outputVars, ReferenceMetadata metadata,
                            Map<String, StreamSpec> streamSpecs, Map<String, InputStream> dataStreams) {
    // target stream is a special root stream which excludes no children
    super(targetEntity, computedEntity, metadata, streamSpecs, dataStreams, Collections.emptyList());

    // header names for values we will return
    _outputVars = VariableDef.toDotNotation(outputVars).toArray(new String[outputVars.size()]);
    _outputRow = new InitialSizeStringMap.Builder(_outputVars).build();

    // build ancestor streams for any required ancestors
    _ancestorStreams = new LinkedHashMap<>();
    List<EntityDef> ancestors = metadata.getAncestors(targetEntity);
    for (int i = 0; i < ancestors.size(); i++) {
      EntityDef entity = ancestors.get(i);
      if (dataStreams.containsKey(entity.getId())) {
        List<String> descendantsToExclude = getSubtreeEntityIds(i == 0 ? targetEntity : ancestors.get(i - 1));
        RootEntityStream stream = new RootEntityStream(entity, computedEntity, metadata, streamSpecs, dataStreams, descendantsToExclude);
        _ancestorStreams.put(entity.getId(), stream);
      }
    }
  }

  private List<String> getSubtreeEntityIds(EntityDef rootEntity) {
    List<String> subtreeIds = new ArrayList<>();
    subtreeIds.add(rootEntity.getId());
    subtreeIds.addAll(_metadata
        .getDescendants(rootEntity).stream()
        .map(EntityDef::getId)
        .toList());
    return subtreeIds;
  }

  @Override
  public Map<String, String> next() {
    // RootEntityStream.next() will give us our own native vars plus any pulled from descendants
    Map<String, String> row = super.next();

    // supplement with vars from ancestors
    for (RootEntityStream ancestorStream : _ancestorStreams.values()) {
      String ancestorIdColName = ancestorStream.getEntityIdColName();
      Predicate<Map<String,String>> isMatch = r -> r.get(ancestorIdColName).equals(row.get(ancestorIdColName));
      Optional<Map<String,String>> ancestorRow = ancestorStream.getPreviousRowIf(isMatch);
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
    for (String col : _outputVars) {
      _outputRow.put(col, row.get(col));
    }
    return _outputRow;
  }
}
