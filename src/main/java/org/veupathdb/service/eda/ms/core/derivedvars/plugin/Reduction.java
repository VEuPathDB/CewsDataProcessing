package org.veupathdb.service.eda.ms.core.derivedvars.plugin;

import java.util.Map;
import java.util.function.Supplier;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.reductions.Mean;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.reductions.Sum;

public abstract class Reduction extends DerivedVariablePlugin {

  static Map<String, Supplier<Reduction>> getPlugins() {
    return pluginsOf(Reduction.class,
      // available reductions
      Sum.class,
      Mean.class
    );
  }

  public abstract void addRow(Map<String,String> nextRow);

  public abstract String getResultingValue();

  @Override
  protected void validateSourceEntity(String sourceEntityId, String targetEntityId) throws ValidationException {

  }
}
