package org.veupathdb.service.eda.ms.core.derivedvars;

import java.util.Collections;
import java.util.List;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.Reduction;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.Transform;

public class DerivedVariableFactory {

  public DerivedVariableFactory(ReferenceMetadata metadata) {

  }

  public List<Transform> getTransforms(EntityDef targetEntity) {
    return Collections.emptyList();
  }

  public List<Reduction> getReductions(EntityDef sourceEntity, EntityDef targetEntity) {
    return Collections.emptyList();
  }
}
