package org.veupathdb.service.eda.ms.plugins.transforms;

import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.fgputil.script.Scripting;
import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.ms.core.derivedvars.Transform;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.*;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class EcmaScriptExpressionEval extends Transform<EcmaScriptExpressionEvalConfig> {

  private static final String JS_FUNCTION_NAME = "evalExpression";

  private String _scriptExpression;
  private List<VariableReference> _variableRefs;
  private APIVariableType _expectedType;
  private APIVariableDataShape _expectedShape;
  private ScriptEngine _engine;
  private List<TwoTuple<String, APIVariableType>> _scriptParams;

  @Override
  protected Class<EcmaScriptExpressionEvalConfig> getConfigClass() {
    return EcmaScriptExpressionEvalConfig.class;
  }

  @Override
  protected void acceptConfig(EcmaScriptExpressionEvalConfig config) throws ValidationException {
    _scriptExpression = config.getEcmaScriptExpression();
    _variableRefs = config.getInputVariables();
    _expectedType = config.getExpectedType();
    _expectedShape = config.getExpectedShape();
  }

  @Override
  protected void performSupplementalDependedVariableValidation() throws ValidationException {
    try {
      for (VariableSpec spec : getRequiredInputVars()) {
        checkVariable("Input variable", spec, List.of(
            // only allow these input types for now
            APIVariableType.INTEGER,
            APIVariableType.NUMBER,
            APIVariableType.STRING
        ), null); // any shape is fine
      }
      // need to wait and set up the script engine here since we need to know the types of the input vars
      _engine = Scripting.getScriptEngine(Scripting.Language.JAVASCRIPT);
      String parameterList = _variableRefs.stream().map(VariableReference::getName).collect(Collectors.joining(", "));
      _engine.eval("function " + JS_FUNCTION_NAME + "(" + parameterList + ") { return " + _scriptExpression + "; }");
      _scriptParams = _variableRefs.stream()
          .map(ref -> _metadata.getVariable(ref.getVariable()).orElseThrow())
          .map(var -> new TwoTuple<>(VariableDef.toDotNotation(var), var.getType()))
          .toList();
    }
    catch (ScriptException e) {
      throw new ValidationException("JavaScript expression is not valid: " + _scriptExpression);
    }
  }

  @Override
  public String getFunctionName() {
    return "ecmaScriptExpressionEval";
  }

  @Override
  public List<VariableSpec> getRequiredInputVars() {
    return _variableRefs.stream().map(VariableReference::getVariable).toList();
  }

  @Override
  public APIVariableType getVariableType() {
    return _expectedType;
  }

  @Override
  public APIVariableDataShape getDataShape() {
    return _expectedShape;
  }

  /*************************************************************
   * TODO: TBD what about range, vocab, units?
   *************************************************************/

  @Override
  public String getValue(Map<String, String> row) {
    try {
      List<Object> parameters = _scriptParams.stream()
          .map(var -> {
            String valueStr = row.get(var.getKey());
            if (valueStr.isEmpty()) throw new NoSuchElementException();
            Object obj = switch (var.getSecond()) {
              case INTEGER -> Integer.parseInt(valueStr);
              case NUMBER -> Double.parseDouble(valueStr);
              case STRING -> valueStr;
              default -> Functions.doThrow(() -> new IllegalStateException("Should already have checked variable types."));
            };
            return obj;
          })
          .toList();
      return callFunction(parameters);
    }
    catch (NoSuchElementException e) {
      // expected if a column has an empty value; return empty value in that case
      return "";
    }
  }

  private String callFunction(List<Object> args) {
    try {
      return (String)((Invocable)_engine).invokeFunction(JS_FUNCTION_NAME, args.toArray());
    }
    catch (NoSuchMethodException e) {
      // this should never happen since function is defined above (and should already have compiled)
      throw new RuntimeException("Function called that was not defined in script engine.", e);
    }
    catch (ScriptException e) {
      throw new RuntimeException("Unable to perform expression evaluation with the following args: " +
          args.stream().map(Object::toString).collect(Collectors.joining(", ")));
    }
  }
}
