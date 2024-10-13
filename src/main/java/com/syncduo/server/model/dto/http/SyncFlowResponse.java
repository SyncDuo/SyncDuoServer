package com.syncduo.server.model.dto.http;


import lombok.Data;

@Data
public class SyncFlowResponse {

    private Integer code;

    private String message;

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
