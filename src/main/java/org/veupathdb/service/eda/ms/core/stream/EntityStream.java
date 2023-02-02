package org.veupathdb.service.eda.ms.core.stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.DelimitedDataParser;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.common.derivedvars.DerivedVariableFactory;
import org.veupathdb.service.eda.common.derivedvars.plugin.Transform;

import static org.gusdb.fgputil.FormatUtil.NL;
import static org.gusdb.fgputil.FormatUtil.TAB;

/**
 * Base class for various entity streams, which handles reading tabular data
 * into a map for each row and caching the last row read for inspection
 * before delivery.
 */
public class EntityStream implements Iterator<Map<String,String>> {

  private static final Logger LOG = LogManager.getLogger(EntityStream.class);

  // final fields used to set up the stream
  protected final ReferenceMetadata _metadata;
  protected final DerivedVariableFactory _derivedVariableFactory;
  protected final EntityDef _entity;
  private final List<VariableDef> _expectedNativeColumns;
  private final DelimitedDataParser _parser;
  private final BufferedReader _reader;
  private final List<String> _nativeHeaders;

  // caches the last row read from the scanner (null if no more rows)
  private Map<String, String> _lastRowRead;

  public EntityStream(StreamSpec spec, InputStream inStream, ReferenceMetadata metadata) {
    LOG.info("Instantiated " + getClass().getSimpleName() + " for entity " + spec.getEntityId());
    _metadata = metadata;
    _derivedVariableFactory = new DerivedVariableFactory(metadata);
    _entity = _metadata.getEntity(spec.getEntityId()).orElseThrow();
    _expectedNativeColumns = _metadata.getTabularColumns(_entity, spec);
    _nativeHeaders = VariableDef.toDotNotation(_expectedNativeColumns);
    _parser = new DelimitedDataParser(_nativeHeaders, TAB, true);
    _reader = beginValidatedInput(inStream);
    _lastRowRead = readRow();
  }

  private BufferedReader beginValidatedInput(InputStream inStream) {
    final InputStreamReader inputStreamReader = new InputStreamReader(inStream);
    final BufferedReader reader = new BufferedReader(inputStreamReader);
    try {
      final String headerLine = reader.readLine();
      // capture the header and validate response
      if (headerLine == null) {
        throw new RuntimeException("Subsetting service tabular endpoint did not return header row");
      }
      Map<String,String> header = _parser.parseLine(headerLine); // validates counts
      List<String> received = new ArrayList<>(header.values());
      for (int i = 0; i < received.size(); i++) {
        if (!received.get(i).equals(_expectedNativeColumns.get(i).getVariableId())) {
          throw new RuntimeException("Tabular subsetting result of type '" +
              _entity.getId() + "' contained unexpected header." + NL + "Expected:" +
              _expectedNativeColumns.stream().map(v -> v.getVariableId()).collect(Collectors.joining(",")) +
              NL + "Found   : " + String.join(",", received));
        }
      }
      return reader;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Set<String> getNativeHeaders() {
    return new HashSet<>(_nativeHeaders);
  }

  public EntityDef getEntity() {
    return _entity;
  }

  // returns null if no more rows
  private Map<String, String> readRow() {
    try {
      final String nextLine = _reader.readLine();
      return nextLine != null
          ? applyTransforms(_parser.parseLine(nextLine))
          : null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean hasNext() {
    return _lastRowRead != null;
  }

  @Override
  public Map<String, String> next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No rows remain.");
    }
    Map<String, String> lastRow = _lastRowRead;
    _lastRowRead = readRow();
    return lastRow;
  }

  public Optional<Map<String, String>> getPreviousRowIf(Predicate<Map<String, String>> condition) {
    return hasNext() && condition.test(_lastRowRead)
      ? Optional.of(_lastRowRead)
      : Optional.empty();
  }

  public Optional<Map<String, String>> getNextRowIf(Predicate<Map<String, String>> condition) {
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
  protected Map<String,String> applyTransforms(Map<String,String> row) {
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
