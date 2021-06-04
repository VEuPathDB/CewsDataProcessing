package org.veupathdb.service.eda.ms.core.stream;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.DelimitedDataParser;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.ms.core.derivedvars.DerivedVariableFactory;
import org.veupathdb.service.eda.ms.core.derivedvars.plugin.Transform;

import static org.gusdb.fgputil.FormatUtil.NL;
import static org.gusdb.fgputil.FormatUtil.TAB;

public class EntityStream implements Iterator<LinkedHashMap<String,String>> {

  private static final Logger LOG = LogManager.getLogger(EntityStream.class);

  // final fields used to set up the stream
  protected final ReferenceMetadata _metadata;
  protected final DerivedVariableFactory _derivedVariableFactory;
  protected final EntityDef _entity;
  private final List<VariableDef> _expectedNativeColumns;
  private final DelimitedDataParser _parser;
  private final Scanner _scanner;

  // caches the last row read from the scanner (null if no more rows)
  private LinkedHashMap<String, String> _lastRowRead;

  public EntityStream(StreamSpec spec, InputStream inStream, ReferenceMetadata metadata) {
    LOG.info("Instantiated " + getClass().getSimpleName() + " for entity " + spec.getEntityId());
    _metadata = metadata;
    _derivedVariableFactory = new DerivedVariableFactory(metadata);
    _entity = _metadata.getEntity(spec.getEntityId()).orElseThrow();
    _expectedNativeColumns = _metadata.getTabularColumns(_entity, spec);
    _parser = new DelimitedDataParser(VariableDef.toDotNotation(_expectedNativeColumns), TAB, true);
    _scanner = beginValidatedInput(inStream);
    _lastRowRead = readRow();
  }

  private Scanner beginValidatedInput(InputStream inStream) {
    Scanner scanner = new Scanner(inStream);
    if (!scanner.hasNext()) {
      throw new RuntimeException("Subsetting service tabular endpoint did not return header row");
    }

    // capture the header and validate response
    Map<String,String> header = _parser.parseLine(scanner.nextLine()); // validates counts
    for (Entry<String,String> headerColumnName : header.entrySet()) {
      if (!headerColumnName.getKey().equals(headerColumnName.getValue())) {
        throw new RuntimeException("Tabular subsetting result of type '" +
            _entity.getId() + "' contained unexpected header." + NL +
            "Expected: " + header.keySet().stream().collect(Collectors.joining(", ")) + NL +
            "Found   : " + header.values().stream().collect(Collectors.joining(", ")));
      }
    }
    return scanner;
  }

  public EntityDef getEntity() {
    return _entity;
  }

  // returns null if no more rows
  private LinkedHashMap<String, String> readRow() {
    return _scanner.hasNextLine()
        ? applyTransforms(_parser.parseLine(_scanner.nextLine()))
        : null;
  }

  @Override
  public boolean hasNext() {
    return _lastRowRead != null;
  }

  @Override
  public LinkedHashMap<String, String> next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No rows remain.");
    }
    LinkedHashMap<String, String> lastRow = _lastRowRead;
    _lastRowRead = readRow();
    return lastRow;
  }

  public Optional<LinkedHashMap<String, String>> getPreviousRowIf(Predicate<Map<String, String>> condition) {
    return hasNext() && condition.test(_lastRowRead)
      ? Optional.of(_lastRowRead)
      : Optional.empty();
  }

  public Optional<LinkedHashMap<String, String>> getNextRowIf(Predicate<Map<String, String>> condition) {
    return hasNext() && condition.test(_lastRowRead)
      ? Optional.of(next())
      : Optional.empty();
  }

  /**
   * Applies any transformations that:
   *  1. are not already applied
   *  2. can be applied given the available vars
   * Loops through until no more can be applied (handles nested transform case)
   */
  protected LinkedHashMap<String,String> applyTransforms(LinkedHashMap<String,String> row) {
    List<Transform> transforms = _derivedVariableFactory.getTransforms(_entity);
    int numApplied;
    do {
      numApplied = 0;
      for (Transform transform : transforms) {
        String outputColumn = transform.getOutputColumnName();
        if (!row.containsKey(outputColumn) && transform.allRequiredColsPresent(row)) {
          row.put(outputColumn, transform.getValue(row));
          numApplied++;
        }
      }
    }
    while (numApplied > 0);
    return row;
  }
}
