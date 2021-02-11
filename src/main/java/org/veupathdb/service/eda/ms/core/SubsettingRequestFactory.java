package org.veupathdb.service.eda.ms.core;

import java.util.List;
import org.veupathdb.service.eda.generated.model.APIFilter;

public class SubsettingRequestFactory {

  private final String _subsettingServiceUrl;
  private final List<APIFilter> _filters;

  public SubsettingRequestFactory(String subsettingServiceUrl, List<APIFilter> filters) {
    _subsettingServiceUrl = subsettingServiceUrl;
    _filters = filters;
  }
}
