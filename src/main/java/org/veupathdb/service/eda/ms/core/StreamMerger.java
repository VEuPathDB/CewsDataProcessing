package org.veupathdb.service.eda.ms.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.VariableSpec;

import static org.gusdb.fgputil.FormatUtil.NL;
import static org.gusdb.fgputil.FormatUtil.TAB;

public class StreamMerger {

  public static void writeMergedStream(ReferenceMetadata metadata, String targetEntityId,
      List<VariableSpec> requestedVars, List<StreamSpec> requiredStreams,
      Map<String, InputStream> dataStreams, OutputStream out) {

    EntityDef targetEntity = metadata.getEntity(targetEntityId);
    List<VariableSpec> outputVars = getOutputVars(targetEntity, requestedVars, metadata);

    Map<String,SingleEntityStream> entityStreamMap = buildEntityStreamMap(metadata, requiredStreams, dataStreams);

    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out))) {

      // write the header row
      writer.write(getHeaderRow(targetEntity, outputVars));

      // write the entity rows
      entityStreamMap.get(targetEntityId).writeMergedTabularOutput(writer);

      // flush any remaining chars
      writer.flush();
    }
    catch (IOException e) {
      throw new RuntimeException("Unable to write output stream", e);
    }
  }

  private static List<VariableSpec> getOutputVars(EntityDef targetEntity,
      List<VariableSpec> requestedVars, ReferenceMetadata metadata) {
    List<VariableSpec> outputVars = new ArrayList<>();
    outputVars.add(VariableDef.newVariableSpec(targetEntity.getId(), targetEntity.getIdColumnName()));
    for (EntityDef ancestor : metadata.getAncestors(targetEntity)) {
      outputVars.add(VariableDef.newVariableSpec(ancestor.getId(), ancestor.getIdColumnName()));
    }
    outputVars.addAll(requestedVars);
    return outputVars;
  }

  private static String getHeaderRow(EntityDef targetEntity, List<VariableSpec> outputVars) {
    return outputVars.stream()
      .map(VariableDef::toDotNotation)
      .collect(Collectors.joining(TAB)) + NL;
  }

  private static Map<String, SingleEntityStream> buildEntityStreamMap(ReferenceMetadata metadata,
      List<StreamSpec> requiredStreams, Map<String, InputStream> dataStreams) {
    Map<String, SingleEntityStream> entityStreamMap = new HashMap<>();
    for (StreamSpec spec : requiredStreams) {
      entityStreamMap.put(spec.getEntityId(), new SingleEntityStream(spec, dataStreams.get(spec.getStreamName()), metadata));
    }
    return entityStreamMap;
  }

}
