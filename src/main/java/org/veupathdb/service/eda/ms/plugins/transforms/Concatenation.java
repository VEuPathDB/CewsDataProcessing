package org.veupathdb.service.eda.ms.plugins.transforms;

import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.ms.core.derivedvars.Transform;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Concatenation extends Transform<ConcatenationConfig> {

  private ConcatenationConfig _config;
  private List<String> _inputColumnNames;

  @Override
  public String getFunctionName() {
    return "concatenation";
  }

  @Override
  protected Class<ConcatenationConfig> getConfigClass() {
    return ConcatenationConfig.class;
  }

  @Override
  protected void acceptConfig(ConcatenationConfig config) throws ValidationException {
    _config = config;
    _inputColumnNames = config.getInputVariables().stream().map(VariableDef::toDotNotation).collect(Collectors.toList());
  }

  @Override
  protected void performSupplementalDependedVariableValidation() throws ValidationException {
    // nothing else to do here
  }

  @Override
  public List<VariableSpec> getRequiredInputVars() {
    return _config.getInputVariables();
  }

  @Override
  public String getValue(Map<String, String> row) {
    return new StringBuilder()
        .append(_config.getPrefix())
        .append(_inputColumnNames.stream()
            .map(row::get)
            .collect(Collectors.joining(_config.getDelimiter())))
        .append(_config.getSuffix())
        .toString();
  }

  @Override
  public APIVariableType getVariableType() {
    return APIVariableType.STRING;
  }

  @Override
  public APIVariableDataShape getDataShape() {
    return APIVariableDataShape.CONTINUOUS;
  }

}
