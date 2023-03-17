package org.veupathdb.service.eda.ms.core.stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gusdb.fgputil.DelimitedDataParser;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.EntityDef;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;
import org.veupathdb.service.eda.common.model.VariableDef;
import org.veupathdb.service.eda.generated.model.VariableSpecImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gusdb.fgputil.FormatUtil.NL;
import static org.gusdb.fgputil.FormatUtil.TAB;

/**
 * Base class for entity streams; handles reading tabular data into a map for
 * each row and caching the last row read for inspection before delivery.
 * Serves as a base class for stream processor nodes that abstracts away the
 * reading of the tabular data (leaving tree-related and derived variable logic
 * to the superclass).
 *
 * The lifecycle of this class is:
 * 1. construction
 * 2. assignment of the stream spec (typically by the subclass in its constructor)
 * 3. assignment of the data stream
 * 4. reading tabular rows from stream and outputting data Maps as requested
 */
public class EntityStream implements Iterator<Map<String,String>> {

  private static final Logger LOG = LogManager.getLogger(EntityStream.class);

  // final fields
  protected final ReferenceMetadata _metadata;

  // fields set up by assignment of stream spec
  private StreamSpec _streamSpec;
  private String _entityIdColumnName;
  private List<VariableDef> _expectedNativeColumns;
  private DelimitedDataParser _parser;

  // fields set up by the assignment of the data stream
  // caches the last row read from the scanner (null if no more rows)
  private BufferedReader _reader;
  private Map<String, String> _lastRowRead;

  protected EntityStream(ReferenceMetadata metadata) {
    _metadata = metadata;
  }

  protected EntityStream setStreamSpec(StreamSpec streamSpec) {
    LOG.info("Initializing " + getClass().getSimpleName() + " for entity " + streamSpec.getEntityId());
    _streamSpec = streamSpec;
    EntityDef entity = _metadata.getEntity(streamSpec.getEntityId()).orElseThrow();
    // cache the name of the column used to identify records that match the current row
    _entityIdColumnName = VariableDef.toDotNotation(entity.getIdColumnDef());
    _expectedNativeColumns = _metadata.getTabularColumns(entity, streamSpec);
    List<String> nativeHeaders = VariableDef.toDotNotation(_expectedNativeColumns);
    _parser = new DelimitedDataParser(nativeHeaders, TAB, true);
    return this;
  }

  protected StreamSpec getStreamSpec() {
    return _streamSpec;
  }

  protected String getEntityIdColumnName() {
    return _entityIdColumnName;
  }

  public void acceptDataStreams(Map<String, InputStream> dataStreams) {
    InputStream inStream = dataStreams.get(_streamSpec.getStreamName());
    if (inStream == null) // not found!
      throw new IllegalStateException("Stream with name " + _streamSpec.getStreamName() + " expected but not distributed.");
    // remove the stream from the map; then enables later checking of whether all streams were distributed
    dataStreams.remove(_streamSpec.getStreamName());
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
        if (!received.get(i).equals(_expectedNativeColumns.get(i).getVariableId())) { // validates header names
          throw new RuntimeException("Tabular subsetting result of type '" +
              _streamSpec.getEntityId() + "' contained unexpected header." + NL + "Expected:" +
              _expectedNativeColumns.stream().map(VariableSpecImpl::getVariableId).collect(Collectors.joining(",")) +
              NL + "Found   : " + String.join(",", received));
        }
      }
      return reader;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // returns null if no more rows
  private Map<String, String> readRow() {
    try {
      final String nextLine = _reader.readLine();
      return nextLine == null ? null : _parser.parseLine(nextLine);
    }
    catch (IOException e) {
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

  @Override
  public String toString() {
    return toString(0);
  }

  public String toString(int indentSize) {
    String indent = IntStream.range(0, indentSize).mapToObj(i -> " ").collect(Collectors.joining());
    return
        indent + "{" + NL +
        indent + "  entityIdColumnName: " + _entityIdColumnName + NL +
        indent + "  expectedNativeColumns: [" + NL +
        _expectedNativeColumns.stream().map(c -> indent + "    " + c.toString() + NL).collect(Collectors.joining()) +
        indent + "  streamSpec: " + NL + toString(_streamSpec, indentSize + 2) + NL +
        indent + "}";
  }

  private String toString(StreamSpec spec, int indentSize) {
    String indent = IntStream.range(0, indentSize).mapToObj(i -> " ").collect(Collectors.joining());
    return indent + "{" + NL +
        indent + "  name: " + spec.getStreamName() + NL +
        indent + "  entityId: " + spec.getEntityId() + NL +
        indent + "  vars: [ " + spec.stream().map(v -> VariableDef.toDotNotation(v)).collect(Collectors.joining()) + " ]" + NL +
        indent + "}";
  }
}
