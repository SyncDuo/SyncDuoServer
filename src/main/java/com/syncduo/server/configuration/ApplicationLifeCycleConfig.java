package com.syncduo.server.configuration;


import com.syncduo.server.bus.FilesystemEventHandler;
import com.syncduo.server.service.bussiness.SystemManagementService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.service.restic.ResticFacadeService;
import com.syncduo.server.service.rslsync.RslSyncFacadeService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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

    private final ResticFacadeService resticFacadeService;

    private final RcloneFacadeService rcloneFacadeService;

    private final RslSyncFacadeService rslSyncFacadeService;

    @Autowired
    public ApplicationLifeCycleConfig(
            SystemManagementService systemManagementService,
            FilesystemEventHandler filesystemEventHandler,
            ResticFacadeService resticFacadeService,
            RcloneFacadeService rcloneFacadeService,
            RslSyncFacadeService rslSyncFacadeService) {
        this.systemManagementService = systemManagementService;
        this.filesystemEventHandler = filesystemEventHandler;
        this.resticFacadeService = resticFacadeService;
        this.rcloneFacadeService = rcloneFacadeService;
        this.rslSyncFacadeService = rslSyncFacadeService;
    }

    @PostConstruct
    public void startUp() {
        log.info("Starting up {} environment", this.activeProfile);
        this.rcloneFacadeService.init();
        this.resticFacadeService.init();
        this.rslSyncFacadeService.init();
        // 系统启动扫描
        this.systemManagementService.rescanAllSyncFlow();
        // 启动 handler
        filesystemEventHandler.startHandle();
    }
}
