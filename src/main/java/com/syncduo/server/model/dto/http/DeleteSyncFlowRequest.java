package com.syncduo.server.model.dto.http;

import lombok.Data;

@Data
public class DeleteSyncFlowRequest {

    private Long source2InternalSyncFlowId;

    private Long internal2ContentSyncFlowId;
}
