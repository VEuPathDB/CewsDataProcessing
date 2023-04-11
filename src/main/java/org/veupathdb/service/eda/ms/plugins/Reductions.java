package org.veupathdb.service.eda.ms.plugins;

import org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariableFactory;
import org.veupathdb.service.eda.ms.core.derivedvars.Reduction;
import org.veupathdb.service.eda.ms.plugins.reductions.Mean;
import org.veupathdb.service.eda.ms.plugins.reductions.RelativeObservationAggregator;
import org.veupathdb.service.eda.ms.plugins.reductions.SubsetMembership;
import org.veupathdb.service.eda.ms.plugins.reductions.Sum;

import static org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariableFactory.pluginsOf;

public class Reductions {
  public static DerivedVariableFactory.PluginMap<Reduction> getPlugins() {
    return pluginsOf(Reduction.class,
        // available reductions
        Sum.class,
        Mean.class,
        SubsetMembership.class,
        RelativeObservationAggregator.class
    );
  }
}
