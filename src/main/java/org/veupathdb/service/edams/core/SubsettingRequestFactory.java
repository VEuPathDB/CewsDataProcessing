package org.veupathdb.service.edams.core;

import java.util.List;
import org.veupathdb.service.edams.generated.model.APIFilter;

public class SubsettingRequestFactory {

  private final String _subsettingServiceUrl;
  private final List<APIFilter> _filters;

  public SubsettingRequestFactory(String subsettingServiceUrl, List<APIFilter> filters) {
    _subsettingServiceUrl = subsettingServiceUrl;
    _filters = filters;
  }
}
