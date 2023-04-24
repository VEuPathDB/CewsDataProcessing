package org.veupathdb.service.eda.ms.core.derivedvars;

import jakarta.ws.rs.BadRequestException;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.APIFilter;
import org.veupathdb.service.eda.generated.model.DerivationType;
import org.veupathdb.service.eda.generated.model.VariableSpec;

import java.util.*;

/**
 * Parent class providing logic and interface common to all Reduction derived variables, that is: a derived variable
 * that collects values from rows of a descendant entity and reduces them into a single value.  Instances of this
 * class represent a derived variable with a certain configuration which will be generated across an entire set of
 * rows; to do so, it provides an implementation of a Reducer, which generates a single reduction value (one row on the
 * output entity) and then is thrown away.  A new Reducer is created to produce the reduction derived variable value
 * on the next row.
 *
 * @param <T> type of configuration object for this derived variable plugin
 */
public abstract class Reduction<T> extends AbstractDerivedVariable<T> {

  /**
   * Implementations of this interface produce reduction derived variable values from a set of incoming descendant
   * entity rows.
   */
  public interface Reducer {

    /**
     * Adds a row to this reduction; values should be incorporated into a state where a final value can be returned
     * by getResultingValue()
     *
     * @param nextRow the next row to process
     */
    void addRow(Map<String,String> nextRow);

    /**
     * Produces the resulting value.  After this method is called, no more calls will be made to addRow(); however,
     * it may be called before any calls to addRow occur (if there are zero input rows).  Thus any initialization
     * needed in this class should happen in a constructor or by instantiating fields in their declarations.
     *
     * @return final value for this derived variable
     */
    String getResultingValue();
  }

  /**
   * Returns an implementation of a Reducer which will operate on a set of descendant entity input rows to create a
   * value on this entity's row.  A new Reducer is requested for each row, so it can contain state used to aggregate
   * information before returning a resulting value.
   *
   * @return instance of a Reducer which will create a reduction derived variable value for a single row
   */
  public abstract Reducer createReducer();

  private StreamSpec _inputStreamSpec;

  @Override
  public final DerivationType getDerivationType() {
    return DerivationType.REDUCTION;
  }

  public StreamSpec getInputStreamSpec() {
    if (_inputStreamSpec == null) {
      EntityDef entity = _metadata.getEntity(getCommonEntityId()).orElseThrow();
      _inputStreamSpec = new StreamSpec(UUID.randomUUID().toString(), entity.getId())
          .addVars(getRequiredInputVars().stream()
              // filter out IDs of the requested entity and its ancestors
              .filter(var -> !isVariableInList(var, _metadata.getTabularColumns(entity, Collections.emptyList())))
              .toList())
          .setFiltersOverride(getFiltersOverride());
    }
    return _inputStreamSpec;
  }

  private static boolean isVariableInList(VariableSpec var, List<VariableDef> list) {
    for (VariableSpec listVar : list) {
      if (VariableDef.isSameVariable(listVar, var)) return true;
    }
    return false;
  }

  protected List<APIFilter> getFiltersOverride() {
    return null;
  }

  @Override
  public void validateDependedVariableLocations() {
    // the common entity of the input vars must be the same as or a descendant of the target entity
    String inputVarsEntityId = getCommonEntityId();
    List<String> ancestorIds = _metadata
        .getAncestors(_metadata.getEntity(inputVarsEntityId).orElseThrow())
        .stream().map(EntityDef::getId).toList();
    if (!inputVarsEntityId.equals(getEntityId()) && !ancestorIds.contains(getEntityId())) {
      throw new BadRequestException("Input vars configured for reduction derived var " + getFunctionName() + " are not on the target or a descendant entity.");
    }
  }

  // TODO: using common entity puts a restriction on the vars a reduction can depend on i.e. that they all come
  //    from the same entity (no inherited vars).  But there's no real reason to impose this restriction since
  //    reductions produce StreamingEntityNodes that know how to handle inheritance and derived vars.  Thus, a
  //    better strategy may be to find the "lowest" (farthest from root) entity in the required vars and use that
  //    entity, then ensure required vars are either that entity or an ancestor and let folks use inherited vars here.
  private String getCommonEntityId() {
    // depended vars must all be on the same entity, which must be a descendant of this entity
    String commonEntityId = null;
    for (VariableSpec spec : getRequiredInputVars()) {
      _metadata.getVariable(spec).orElseThrow(() ->
          new BadRequestException("Input variable for reduction derived var " + getFunctionName() + " does not exist."));
      if (commonEntityId == null) {
        commonEntityId = spec.getEntityId();
      }
      else {
        if (!spec.getEntityId().equals(commonEntityId)) {
          throw new BadRequestException("Not all input variables for reduction derived var " + getFunctionName() + " come from the same entity.");
        }
      }
    }
    if (commonEntityId == null) {
      throw new IllegalStateException("No required input vars specified in reduction plugin " + getFunctionName());
    }
    return commonEntityId;
  }
}
