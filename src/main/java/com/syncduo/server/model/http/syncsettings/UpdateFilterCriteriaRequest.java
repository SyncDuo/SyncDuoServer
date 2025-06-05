package com.syncduo.server.model.http.syncsettings;

import lombok.Data;

@Data
public class UpdateFilterCriteriaRequest {
    String syncFlowId;

    String filterCriteria;
}
