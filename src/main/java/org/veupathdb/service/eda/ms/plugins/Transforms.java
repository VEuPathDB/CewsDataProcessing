package org.veupathdb.service.eda.ms.plugins;

import org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariableFactory;
import org.veupathdb.service.eda.ms.core.derivedvars.Transform;
import org.veupathdb.service.eda.ms.plugins.transforms.*;

import static org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariableFactory.pluginsOf;

public class Transforms {
  public static DerivedVariableFactory.PluginMap<Transform> getPlugins() {
    return pluginsOf(Transform.class,
        // available transforms
        Concatenation.class,
        BodyMassIndex.class,
        CategoricalRecoding.class,
        ContinuousNumericToOrdinal.class,
        AdvancedSubset.class,
        EcmaScriptExpressionEval.class,
        RelativeObservationMinTimeInterval.class,
        UnitConversion.class
    );
  }
}
