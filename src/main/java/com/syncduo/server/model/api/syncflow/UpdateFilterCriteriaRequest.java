package com.syncduo.server.model.api.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UpdateFilterCriteriaRequest {
    String syncFlowId;

    @JsonIgnore
    private long syncFlowIdInner;

    String filterCriteria;
}
