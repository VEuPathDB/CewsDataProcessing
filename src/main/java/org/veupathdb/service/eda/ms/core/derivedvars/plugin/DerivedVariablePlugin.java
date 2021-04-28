package org.veupathdb.service.eda.ms.core.derivedvars.plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;

import static org.gusdb.fgputil.functional.Functions.wrapException;

public abstract class DerivedVariablePlugin {

  protected ReferenceMetadata _metadata;

  protected List<VariableDef> _inputVariables;
  protected List<String> _inputColumnNames;

  private VariableDef _outputVariable;
  private String _outputColumnName;

  public static <T extends DerivedVariablePlugin> Map<String, Supplier<T>> pluginsOf(Class<T> subtype, Class<? extends T>... implementations) {
    Map<String, Supplier<T>> map = new HashMap<>();
    for (Class<? extends T> plugin : implementations) {
      Supplier<T> supplier = (Supplier<T>)wrapException(() -> plugin.getConstructor().newInstance());
      map.put(supplier.get().getName(), supplier);
    }
    return map;
  }

  protected abstract void validateSourceEntity(String sourceEntityId, String targetEntityId) throws ValidationException;

  /**
   * A combination of validate and initialize any local vars dependent on these vars
   *
   * @param inputVariables input variables set by the caller
   * @throws ValidationException if input variables are invalid
   */
  protected abstract void receiveInputVariables(List<VariableDef> inputVariables) throws ValidationException;

  public String getName() {
    return getClass().getSimpleName().toLowerCase();
  }

  public DerivedVariablePlugin setMetadata(ReferenceMetadata metadata) {
    _metadata = metadata;
    return this;
  }

  public DerivedVariablePlugin setOutputVariable(VariableDef outputVariable) {
    _outputVariable = outputVariable;
    _outputColumnName = VariableDef.toDotNotation(outputVariable);
    return this;
  }

  public String getOutputColumnName() {
    return _outputColumnName;
  }

  public final DerivedVariablePlugin setInputVariables(List<VariableDef> inputVariables) throws ValidationException {
    String sourceEntityId = getCommonEntity(inputVariables);
    validateSourceEntity(sourceEntityId, _outputVariable.getEntityId());
    receiveInputVariables(inputVariables);
    _inputVariables = inputVariables;
    _inputColumnNames = VariableDef.toDotNotation(inputVariables);
    return this;
  }

  private static String getCommonEntity(List<VariableDef> inputVariables) throws ValidationException {
    if (inputVariables.isEmpty()) throw new ValidationException("At least one input variable is required.");
    String entityId = inputVariables.get(0).getEntityId();
    for (int i = 1; i < inputVariables.size(); i++) {
      if (!inputVariables.get(i).getEntityId().equals(entityId)) {
        throw new ValidationException("All input variables must be members of the same entity.");
      }
    }
    return entityId;
  }

  public boolean allRequiredColsPresent(Map<String, String> row) {
    Collection<String> availableCols = row.keySet();
    for (String requiredCol : _inputColumnNames) {
      if (!availableCols.contains(requiredCol)) {
        return false;
      }
    }
    return true;
  }
}
