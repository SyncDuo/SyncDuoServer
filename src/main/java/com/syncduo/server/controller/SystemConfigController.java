package com.syncduo.server.controller;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.model.api.systemconfig.SystemConfigResponse;
import com.syncduo.server.service.db.impl.*;
import com.syncduo.server.util.EntityValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


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

    @PostMapping("/create-system-config")
    public SystemConfigResponse createSystemConfig(@RequestBody SystemConfigEntity systemConfigEntity) {
        try {
            EntityValidationUtil.isCreateSystemConfigValid(systemConfigEntity);
            this.systemConfigService.createSystemConfig(systemConfigEntity);
            return SystemConfigResponse.onSuccess("创建 system config 成功", systemConfigEntity);
        } catch (SyncDuoException e) {
            log.error("createSystemConfig failed.", e);
            return SystemConfigResponse.onError(e.getMessage());
        }
    }

    @PostMapping("/update-system-config")
    public SystemConfigResponse updateSystemConfig(@RequestBody SystemConfigEntity systemConfigEntity) {
        try {
            EntityValidationUtil.isUpdateSystemConfigRequestValid(systemConfigEntity);
            SystemConfigEntity updatedSystemConfig =
                    this.systemConfigService.updateSystemConfig(systemConfigEntity);
            return SystemConfigResponse.onSuccess("修改 system config 成功", updatedSystemConfig);
        } catch (SyncDuoException e) {
            log.error("updateSystemConfig failed.", e);
            return SystemConfigResponse.onError(e.getMessage());
        }
    }
}
