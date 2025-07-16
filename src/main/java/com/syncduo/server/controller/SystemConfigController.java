package com.syncduo.server.controller;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.model.api.systemconfig.SystemConfigResponse;
import com.syncduo.server.service.db.impl.*;
import com.syncduo.server.util.EntityValidationUtil;
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
        return SystemConfigResponse.onSuccess("获取 systemConfig 成功", systemConfig);
    }

    @PostMapping("/update-system-config")
    public SystemConfigResponse updateSystemConfig(@RequestBody SystemConfigEntity systemConfigEntity) {
        try {
            EntityValidationUtil.isSystemConfigEntityValid(systemConfigEntity);
            SystemConfigEntity updatedSystemConfig =
                    this.systemConfigService.updateSystemConfig(systemConfigEntity);
            return SystemConfigResponse.onSuccess("修改 system config 成功", updatedSystemConfig);
        } catch (SyncDuoException e) {
            log.error("updateSystemConfig failed.", e);
            return SystemConfigResponse.onError(e.getMessage());
        }
    }
}
