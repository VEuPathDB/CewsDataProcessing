package org.veupathdb.service.eda.ms.core.derivedvars.plugin;

import java.util.Map;
import java.util.function.Supplier;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.transforms.Concatenation;

public abstract class Transform extends DerivedVariablePlugin {

  static Map<String, Supplier<Transform>> getPlugins() {
    return pluginsOf(Transform.class,
      // available transforms
      Concatenation.class
    );
  }

  public abstract String getValue(Map<String,String> row);

  @Override
  protected void validateSourceEntity(String sourceEntityId, String targetEntityId) throws ValidationException {
    if (!sourceEntityId.equals(targetEntityId)) {
      throw new ValidationException("Input variables for a transform must be the same as " +
          "the output variable (input=" + sourceEntityId + ", output=" + targetEntityId + ").");
    }
  }
}
