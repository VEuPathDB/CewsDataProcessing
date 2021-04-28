package org.veupathdb.service.eda.ms.core.derivedvars.plugin.reductions;

import java.util.Map;

public class Mean extends SingleNumericVarReduction {

  private int _numRows = 0;
  private double _sum = 0D;

  @Override
  public void addRow(Map<String, String> nextRow) {
    _numRows++;
    _sum += Double.parseDouble(nextRow.get(getTargetColumnName()));
  }

  @Override
  public String getResultingValue() {
    return String.valueOf(_sum / _numRows);
  }
}
