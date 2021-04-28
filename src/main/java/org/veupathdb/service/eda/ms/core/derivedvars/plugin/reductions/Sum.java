package org.veupathdb.service.eda.ms.core.derivedvars.plugin.reductions;

import java.util.Map;

public class Sum extends SingleNumericVarReduction {

  private Double _sum = 0D;

  @Override
  public void addRow(Map<String, String> nextRow) {
    _sum += Double.parseDouble(nextRow.get(getTargetColumnName()));
  }

  @Override
  public String getResultingValue() {
    return String.valueOf(_sum);
  }
}
