package com.syncduo.server.model.dto.http.syncflow;

import lombok.Data;

@Data
public class DeleteSyncFlowRequest {

    private Long source2InternalSyncFlowId;

    private Long internal2ContentSyncFlowId;
}
