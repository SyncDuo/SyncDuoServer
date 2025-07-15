package com.syncduo.server.controller;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.model.api.systemconfig.SystemConfigResponse;
import com.syncduo.server.model.api.systemconfig.UpdateSystemConfigRequest;
import com.syncduo.server.service.db.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;


@RestController
@RequestMapping("/system-config")
@Slf4j
@CrossOrigin
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @Autowired
    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/get-system-config")
    public SystemConfigResponse getSystemConfig() {
        SystemConfigEntity systemConfig = this.systemConfigService.getSystemConfig();
        if (ObjectUtils.isEmpty(systemConfig)) {
            return SystemConfigResponse.onSuccess("没有 system config");
        }
        Map<String, String> systemConfigMap = objectToMap(systemConfig);
        return SystemConfigResponse.onSuccess("获取 systemConfig 成功", systemConfigMap);
    }

    @PostMapping("/update-system-config")
    public SystemConfigResponse updateSystemConfig(@RequestBody UpdateSystemConfigRequest updateSystemConfigRequest) {
        if (ObjectUtils.isEmpty(updateSystemConfigRequest)) {
            return SystemConfigResponse.onError("修改 system config 失败. systemConfigUpdateRequest 为空");
        }
        if (ObjectUtils.isEmpty(updateSystemConfigRequest.getSystemConfigMap())) {
            return SystemConfigResponse.onError("修改 system config 失败. systemConfigMap 为空");
        }
        try {
            Map<String, String> systemConfigMap = updateSystemConfigRequest.getSystemConfigMap();
            SystemConfigEntity systemConfigEntity = systemConfigMapToObj(systemConfigMap);
            SystemConfigEntity updatedSystemConfig =
                    this.systemConfigService.updateSystemConfig(systemConfigEntity);
            Map<String, String> updatedSystemConfigMap = objectToMap(updatedSystemConfig);
            return SystemConfigResponse.onSuccess("修改 system config 成功", updatedSystemConfigMap);
        } catch (SyncDuoException e) {
            log.error("updateSystemConfig failed.", e);
            return SystemConfigResponse.onError(e.getMessage());
        }
    }

    private static Map<String, String> objectToMap(Object obj) {
        Map<String, String> result = new HashMap<>();
        if (ObjectUtils.isEmpty(obj)) {
            return result;
        }
        // 遍历对象, 转换成 map
        Class<?> clazz = obj.getClass();
        while (ObjectUtils.isNotEmpty(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    result.put(field.getName(), String.valueOf(value));
                } catch (IllegalAccessException e) {
                    log.error("转换 SystemConfigEntity 的 field {} 失败", field.getName(), new SyncDuoException(e));
                    // Log or handle as needed
                    result.put(field.getName(), "ERROR");
                }
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    private static SystemConfigEntity systemConfigMapToObj(Map<String, String> systemConfigMap) throws SyncDuoException {
        if (MapUtils.isEmpty(systemConfigMap)) {
            return null;
        }
        SystemConfigEntity obj = new SystemConfigEntity();
        Class<?> clazz = obj.getClass();
        while (ObjectUtils.isNotEmpty(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                String fieldName = field.getName();
                if (systemConfigMap.containsKey(fieldName)) {
                    field.setAccessible(true);
                    String value = systemConfigMap.get(fieldName);
                    try {
                        Object convertedValue = convertStringToType(value, field.getType());
                        field.set(obj, convertedValue);
                    } catch (SyncDuoException | IllegalAccessException | IllegalArgumentException e) {
                        throw new SyncDuoException("转换 systemConfigMap 的 %s 失败".formatted(field.getName()), e);
                    }
                }
            }
            clazz = clazz.getSuperclass(); // handle inheritance
        }
        return obj;
    }

    private static Object convertStringToType(String value, Class<?> type) throws SyncDuoException {
        if (StringUtils.isBlank(value)) return null;
        if (type == String.class) return value;
        if (type == Integer.class) return Integer.valueOf(value);
        if (type == Long.class) return Long.valueOf(value);
        if (type == Double.class) return Double.valueOf(value);
        if (type == Float.class) return Float.valueOf(value);
        if (type == Boolean.class) return Boolean.valueOf(value);
        if (type == Short.class) return Short.valueOf(value);
        if (type == Byte.class) return Byte.valueOf(value);
        if (type == Character.class && value.length() == 1) return value.charAt(0);
        if (type == Timestamp.class) return new Timestamp(Long.parseLong(value));

        throw new SyncDuoException("Unsupported type: " + type.getName());
    }
}
