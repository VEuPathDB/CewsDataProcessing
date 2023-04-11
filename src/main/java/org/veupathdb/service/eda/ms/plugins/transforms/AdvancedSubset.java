package org.veupathdb.service.eda.ms.plugins.transforms;

import jakarta.ws.rs.BadRequestException;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.ms.core.derivedvars.Transform;
import org.veupathdb.service.eda.ms.plugins.reductions.SubsetMembership;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class AdvancedSubset extends Transform<AdvancedSubsetConfig> {

  public static final List<String> DEFAULT_TRUE_VALUES = List.of("1", "true", "yes");

  private List<VariableSpec> _requiredVars;
  private OperationNode _operationTree;

  @Override
  protected Class<AdvancedSubsetConfig> getConfigClass() {
    return AdvancedSubsetConfig.class;
  }

  @Override
  protected void acceptConfig(AdvancedSubsetConfig config) throws ValidationException {
    List<AdvancedSubsetInputVar> inputVariables = config.getInputVariables();
    _requiredVars = inputVariables.stream().map(AdvancedSubsetInputVar::getVariable).toList();
    _operationTree = new OperationNode(config.getOperationTree(), inputVariables);
  }

  @Override
  protected void performSupplementalDependedVariableValidation() {
    // no additional validation needed
  }

  @Override
  public String getFunctionName() {
    return "advancedSubset";
  }

  @Override
  public List<VariableSpec> getRequiredInputVars() {
    return _requiredVars;
  }

  @Override
  public APIVariableType getVariableType() {
    return APIVariableType.INTEGER;
  }

  @Override
  public APIVariableDataShape getDataShape() {
    return APIVariableDataShape.BINARY;
  }

  @Override
  public String getValue(Map<String, String> row) {
    return _operationTree.test(row)
        ? SubsetMembership.RETURNED_TRUE_VALUE
        : SubsetMembership.RETURNED_FALSE_VALUE;
  }

  private enum Operation implements BiPredicate<Boolean, Boolean> {
    INTERSECT ((a, b) -> a && b),
    UNION     ((a, b) -> a || b),
    MINUS     ((a, b) -> a && !b);

    private final BiPredicate<Boolean, Boolean> _function;

    Operation(BiPredicate<Boolean, Boolean> function) {
      _function = function;
    }

    public static Operation getByOperationType(SetOperation.OperationType type) {
      if (type == null) throw new BadRequestException("operation is required");
      return switch (type) {
        case INTERSECT -> INTERSECT;
        case UNION -> UNION;
        case MINUS -> MINUS;
      };
    }

    @Override
    public boolean test(Boolean a, Boolean b) {
      return _function.test(a, b);
    }
  }

  private static class OperationNode implements Predicate<Map<String,String>> {

    private final Operation _op;
    private final Predicate<Map<String,String>> _leftChild;
    private final Predicate<Map<String,String>> _rightChild;

    public OperationNode(SetOperation setOp, List<AdvancedSubsetInputVar> inputVariables) {
      _op = Operation.getByOperationType(setOp.getOperation());
      _leftChild = createChild(
          "left",
          setOp.getLeftVariable(),
          setOp.getLeftOperation(),
          inputVariables);
      _rightChild = createChild(
          "right",
          setOp.getRightVariable(),
          setOp.getRightOperation(),
          inputVariables);
    }

    private Predicate<Map<String,String>> createChild(String childName, String childVarReference,
        SetOperation childOperation, List<AdvancedSubsetInputVar> inputVariables) {
      if ((childVarReference == null && childOperation == null) ||
          (childVarReference != null && childOperation != null)) {
        throw new BadRequestException("Each operation must contain exactly one of: a " + childName + " operation or " + childName + " variable reference.");
      }
      if (childVarReference != null) {
        AdvancedSubsetInputVar inputVar = inputVariables.stream()
            .filter(v -> childVarReference.equals(v.getName())).findAny().orElseThrow(() ->
                new BadRequestException("Reference in operation tree '" + childVarReference + "' that cannot be found in list of input variables."));
        String columnName = VariableDef.toDotNotation(inputVar.getVariable());
        List<String> trueValues = Optional.ofNullable(inputVar.getTrueValues()).orElse(DEFAULT_TRUE_VALUES);
        return row -> trueValues.contains(row.get(columnName));
      }
      else {
        return new OperationNode(childOperation, inputVariables);
      }
    }

    @Override
    public boolean test(Map<String, String> row) {
      return _op.test(_leftChild.test(row), _rightChild.test(row));
    }
  }
}
