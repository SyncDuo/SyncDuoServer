package com.syncduo.server.model.http.syncflow;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChangeSyncFlowStatusRequest {
    String syncFlowId;

    String syncFlowStatus;
}
