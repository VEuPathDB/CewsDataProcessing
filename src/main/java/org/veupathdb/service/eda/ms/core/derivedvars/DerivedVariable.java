package org.veupathdb.service.eda.ms.core.derivedvars;

import org.gusdb.fgputil.validation.ValidationException;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.generated.model.*;

import java.util.List;

public interface DerivedVariable extends DerivedVariableMetadata {

  /**
   * @return name of this derived variable plugin; this is the
   * name used by clients to identify the desired plugin
   */
  String getFunctionName();

  /**
   * Initializes this derived variable instance and assigns and validates config
   *
   * @param metadata metadata for the appropriate study
   * @param spec configuration for this derived variable instance
   */
  void init(ReferenceMetadata metadata, DerivedVariableSpec spec) throws ValidationException;

  /**
   * @return entity of this derived variable
   */
  EntityDef getEntity();

  /**
   * @return column name for this derived variable (dot notation)
   */
  String getColumnName();

  /**
   * @return all required input vars for this derived variable
   */
  List<VariableSpec> getRequiredInputVars();

  /**
   * Validate that the variable specs this derived variable
   * depends on exist and are available on a permitted entity
   */
  void validateDependedVariables() throws ValidationException;

  /**
   * @return any additional derived variables that need to be
   * generated to support this derived var
   */
  List<DerivedVariableSpec> getDependedDerivedVarSpecs();

  /**
   * @return display name for this derived variable
   */
  String getDisplayName();

}
