package com.syncduo.server.model.api.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChangeSyncFlowStatusRequest {
    // "0" stand for all sync flow id
    String syncFlowId;

    @JsonIgnore
    long syncFlowIdInner;

    String syncFlowStatus;

    @JsonIgnore
    SyncFlowStatusEnum syncFlowStatusEnum;
}
