package com.syncduo.server.model.http.systemconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.model.http.syncflow.SyncFlowInfo;
import com.syncduo.server.model.http.syncflow.SyncFlowResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

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
