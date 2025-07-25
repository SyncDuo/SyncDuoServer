package com.syncduo.server.model.api.syncflow;


import lombok.Data;

import java.util.List;

@Data
public class SyncFlowResponse {

    private Integer code;

    private String message;

    private List<SyncFlowInfo> syncFlowInfoList;

    private SyncFlowResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static SyncFlowResponse onSuccess(String message) {
        return new SyncFlowResponse(200, message);
    }

    public static SyncFlowResponse onSuccess(String message, List<SyncFlowInfo> syncFlowInfoList) {
        SyncFlowResponse syncFlowResponse = new SyncFlowResponse(200, message);
        syncFlowResponse.setSyncFlowInfoList(syncFlowInfoList);
        return syncFlowResponse;
    }

    public static SyncFlowResponse onError(String message) {
        return new SyncFlowResponse(500, message);
    }
}
