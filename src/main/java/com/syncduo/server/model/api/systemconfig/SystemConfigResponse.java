package com.syncduo.server.model.api.systemconfig;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Data
@Slf4j
public class SystemConfigResponse {

    private Integer code;

    private String message;

    private Map<String, String> systemConfigMap;

    private SystemConfigResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static SystemConfigResponse onSuccess(String message) {
        return new SystemConfigResponse(200, message);
    }

    public static SystemConfigResponse onError(String message) {
        return new SystemConfigResponse(500, message);
    }

    public static SystemConfigResponse onSuccess(String message, Map<String, String> systemConfigMap) {
        SystemConfigResponse systemConfigResponse = new SystemConfigResponse(200, message);
        systemConfigResponse.setSystemConfigMap(systemConfigMap);
        return systemConfigResponse;
    }
}
