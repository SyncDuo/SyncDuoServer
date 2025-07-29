package com.syncduo.server.configuration;


import com.syncduo.server.bus.FilesystemEventHandler;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.service.bussiness.SystemManagementService;
import com.syncduo.server.service.db.impl.SystemConfigService;
import com.syncduo.server.service.restic.ResticFacadeService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ApplicationLifeCycleConfig {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    private final SystemManagementService systemManagementService;

    private final FilesystemEventHandler filesystemEventHandler;

    private final SystemConfigService systemConfigService;

    private final ResticFacadeService resticFacadeService;

    @Autowired
    public ApplicationLifeCycleConfig(
            SystemManagementService systemManagementService,
            FilesystemEventHandler filesystemEventHandler,
            SystemConfigService systemConfigService,
            ResticFacadeService resticFacadeService) {
        this.systemManagementService = systemManagementService;
        this.filesystemEventHandler = filesystemEventHandler;
        this.systemConfigService = systemConfigService;
        this.resticFacadeService = resticFacadeService;
    }

    @PostConstruct
    public void startUp() throws SyncDuoException {
        SystemConfigEntity systemConfig = this.systemConfigService.getSystemConfig();
        if (ObjectUtils.isEmpty(systemConfig)) {
            log.info("System config is not initialized yet.");
        } else {
            this.resticFacadeService.init();
        }
        // 系统启动扫描
        if ("prod".equals(activeProfile)) {
            log.info("Starting up production environment");
            this.systemManagementService.systemStartUp();
        } else {
            log.info("Starting up development environment");
        }
        // 启动 handler
        filesystemEventHandler.startHandle();
    }
}
