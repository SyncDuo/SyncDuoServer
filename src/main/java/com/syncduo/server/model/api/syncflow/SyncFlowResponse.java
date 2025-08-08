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

    private SyncFlowResponse(Integer code, String message, List<SyncFlowInfo> syncFlowInfoList) {
        this.code = code;
        this.message = message;
        this.syncFlowInfoList = syncFlowInfoList;
    }

    public static SyncFlowResponse onSuccess(String message) {
        return new SyncFlowResponse(200, message);
    }

    public static SyncFlowResponse onSuccess(String message, List<SyncFlowInfo> syncFlowInfoList) {
        return new SyncFlowResponse(200, message, syncFlowInfoList);
    }

    public static SyncFlowResponse onError(String message) {
        return new SyncFlowResponse(500, message);
    }
}
