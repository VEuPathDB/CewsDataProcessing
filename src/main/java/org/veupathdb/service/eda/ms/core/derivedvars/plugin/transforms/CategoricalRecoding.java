package org.veupathdb.service.eda.ms.core.derivedvars.plugin.transforms;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.Transform;
import org.veupathdb.service.eda.common.model.VariableDef;

public class CategoricalRecoding extends Transform {

  @Override
  protected void receiveInputVariables(List<VariableDef> inputVariables) throws ValidationException {
    // only want strings (categorical, ordinal, binary shapes etc) here? 
    // or maybe low cardinality numbers and dates as well?
    if (inputVariables.size() != 1 ||
        !inputVariables.get(0).getType().equals(APIVariableType.STRING)) {
      throw new ValidationException(getName() + " categorical recoding accepts only a single variable of type " + APIVariableType.STRING);
    }
    _targetColumnName = VariableDef.toDotNotation(inputVariables.get(0));
  }

  @Override
  public String getValue(Map<String, String> row) {
    // dont yet know how to get config here
    String[] newValues = getConfig().getNewValues();
    String[][] oldValues = getConfig().getOldValues();
    String valToRecode = row.get(_targetColumnName).toString();

    for (int i; i < newValues.length; i++) {
      for (int j; j < oldValues[i].length; j++) {
        if (oldValues[i][j].equals(valToRecode))
          return(newValues[i]);
      }
    }
    
    return(valToRecode);
  }

}
