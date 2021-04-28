package org.veupathdb.service.eda.ms.core.stream;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.Reduction;

import static org.gusdb.fgputil.functional.Functions.newLinkedHashMapCollector;

public class RootEntityStream extends EntityStream {

  protected final String _entityIdColName;

  private final Map<String, EntityStream> _descendantStreams;

  public RootEntityStream(EntityDef targetEntity, ReferenceMetadata metadata,
      Map<String, StreamSpec> streamSpecs, Map<String, InputStream> dataStreams,
      List<String> descendantsToExclude) {
    super(streamSpecs.get(targetEntity.getId()), dataStreams.get(targetEntity.getId()), metadata);

    // cache the name of the column used to identify records that match the current row
    _entityIdColName = VariableDef.toDotNotation(getEntity().getIdColumnDef());

    // build descendant streams for any required descendants
    _descendantStreams = metadata.getDescendants(targetEntity).stream()
        .filter(entity -> !descendantsToExclude.contains(entity.getId()))
        .filter(entity -> dataStreams.containsKey(entity.getId()))
        .map(entity -> new EntityStream(streamSpecs.get(entity.getId()), dataStreams.get(entity.getId()), metadata))
        .collect(newLinkedHashMapCollector(e -> e.getEntity().getId()));
  }

  protected String getEntityIdColName() {
    return _entityIdColName;
  }

  @Override
  public LinkedHashMap<String, String> next() {
    LinkedHashMap<String,String> nativeVars = super.next();

    // add descendant info reduced by derived vars
    Predicate<Map<String,String>> isMatch = r -> r.get(_entityIdColName).equals(nativeVars.get(_entityIdColName));

    // pull reduction derived vars from each descendant stream
    for (EntityStream descendentStream : _descendantStreams.values()) {

      // get reductions matching this source and target
      List<Reduction> reductions = _derivedVariableFactory.getReductions(descendentStream.getEntity(), getEntity());

      // read rows until no longer matching this entity's ID column
      Optional<LinkedHashMap<String,String>> descendantRow = descendentStream.getNextRowIf(isMatch);
      while (descendantRow.isPresent()) {

        // for each derived var relationship, pass row to builder for that var
        for (Reduction reduction: reductions) {
          reduction.addRow(descendantRow.get());
        }

        // get next row (if matching)
        descendantRow = descendentStream.getNextRowIf(isMatch);
      }

      // no more rows in this descendant stream match this one; build the derived vars
      for (Reduction reduction : reductions) {
        nativeVars.put(reduction.getOutputColumnName(), reduction.getResultingValue());
      }
    }

    // apply transforms again in case they rely on reduction vars
    return applyTransforms(nativeVars);
  }
}
