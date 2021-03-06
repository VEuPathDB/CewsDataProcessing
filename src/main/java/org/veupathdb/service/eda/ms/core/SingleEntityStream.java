package org.veupathdb.service.eda.ms.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Scanner;
import org.veupathdb.service.eda.common.client.spec.StreamSpec;
import org.veupathdb.service.eda.common.model.ReferenceMetadata;

import static org.gusdb.fgputil.FormatUtil.TAB;

public class SingleEntityStream {

  private final StreamSpec _spec;
  private final InputStream _inputStream;
  private final ReferenceMetadata _metadata;

  public SingleEntityStream(StreamSpec spec, InputStream inputStream, ReferenceMetadata metadata) {
    _spec = spec;
    _inputStream = inputStream;
    _metadata = metadata;
  }

  public void writeMergedTabularOutput(Writer writer) throws IOException {

    // get a scanner to the single stream
    Scanner s = new Scanner(_inputStream).useDelimiter("\n");
    if (!s.hasNext()) {
      throw new RuntimeException("Subsetting service tabular endpoint did not return header row");
    }

    // skip the header row for now; later will create a variable map pointing to index of the columns
    String[] headerRow = s.next().split(TAB);

    // "cheat" here and just dump the rows of this one stream (we know there's only one)
    while(s.hasNextLine()) {
      writer.append(s.nextLine()).append('\n');
    }
  }
}
