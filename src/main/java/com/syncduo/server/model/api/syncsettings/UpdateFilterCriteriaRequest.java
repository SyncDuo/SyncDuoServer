package com.syncduo.server.model.api.syncsettings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class UpdateFilterCriteriaRequest {
    String syncFlowId;

    @JsonIgnore
    private long syncFlowIdInner;

    String filterCriteria;
}
