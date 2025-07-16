package com.syncduo.server.model.api.systemconfig;

import com.syncduo.server.model.entity.SystemConfigEntity;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Data
@Slf4j
public class SystemConfigResponse {

    private Integer code;

    private String message;

    private SystemConfigEntity systemConfigEntity;

    private SystemConfigResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static SystemConfigResponse onSuccess(String message) {
        return new SystemConfigResponse(200, message);
    }

    public static SystemConfigResponse onSuccess(String message, SystemConfigEntity systemConfigEntity) {
        SystemConfigResponse result = new SystemConfigResponse(200, message);
        result.setSystemConfigEntity(systemConfigEntity);
        return result;
    }

    public static SystemConfigResponse onError(String message) {
        return new SystemConfigResponse(500, message);
    }
}
