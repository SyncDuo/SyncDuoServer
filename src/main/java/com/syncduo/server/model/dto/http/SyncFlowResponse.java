package com.syncduo.server.model.dto.http;


import lombok.Data;

import java.util.List;

@Data
public class SyncFlowResponse {

    private Integer code;

    private String message;

    // source2InternalSyncFlowId, internal2ContentSyncFlowId
    private List<Long> data;

    private SyncFlowResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static SyncFlowResponse onSuccess(String message) {
        return new SyncFlowResponse(200, message);
    }

    public static SyncFlowResponse onError(String message) {
        return new SyncFlowResponse(500, message);
    }
}
