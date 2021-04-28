package org.veupathdb.service.eda.ms.core.derivedvars.plugin.reductions;

import java.util.List;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.APIVariableType;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.Reduction;

public abstract class SingleNumericVarReduction extends Reduction {

  private String _targetColumnName;

  @Override
  protected void receiveInputVariables(List<VariableDef> inputVariables) throws ValidationException {
    if (inputVariables.size() != 1 ||
        !inputVariables.get(0).getType().equals(APIVariableType.NUMBER)) {
      throw new ValidationException(getName() + " reduction accepts only a single variable of type " + APIVariableType.NUMBER);
    }
    _targetColumnName = VariableDef.toDotNotation(inputVariables.get(0));
  }

  protected String getTargetColumnName() {
    return _targetColumnName;
  }
}
