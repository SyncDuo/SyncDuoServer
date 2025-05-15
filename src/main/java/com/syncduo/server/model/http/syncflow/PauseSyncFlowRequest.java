package com.syncduo.server.model.http.syncflow;

import lombok.Data;

@Data
public class PauseSyncFlowRequest {
    private String syncFlowId;
}
