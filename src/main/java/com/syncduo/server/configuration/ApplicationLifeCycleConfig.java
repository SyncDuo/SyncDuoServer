package com.syncduo.server.configuration;


import com.syncduo.server.bus.FilesystemEventHandler;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.service.bussiness.SystemManagementService;
import com.syncduo.server.service.db.impl.SystemConfigService;
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

    @Value("${syncduo.server.backup.storage.path}")
    private String backupStoragePath;

    private final SystemManagementService systemManagementService;

    private final FilesystemEventHandler filesystemEventHandler;

    private final SystemConfigService systemConfigService;

    @Autowired
    public ApplicationLifeCycleConfig(
            SystemManagementService systemManagementService,
            FilesystemEventHandler filesystemEventHandler,
            SystemConfigService systemConfigService) {
        this.systemManagementService = systemManagementService;
        this.filesystemEventHandler = filesystemEventHandler;
        this.systemConfigService = systemConfigService;
    }

    @PostConstruct
    public void startUp() throws SyncDuoException {
        // 设置备份目录
        SystemConfigEntity systemConfig = new SystemConfigEntity();
        systemConfig.setBackupStoragePath(backupStoragePath);
        this.systemConfigService.createSystemConfig(systemConfig);
        // 系统启动扫描
        if ("prod".equals(activeProfile)) {
            log.info("Starting up production environment");
            // 检查全部 sync-flow 是否同步
            // @PostConstruct 在 @BeforeEach 前面, 会导致在 "旧的folder" 上添加 watcher
            // "旧的folder" 在 @BeforeEach 中删除, 后续触发的事件会发生异常
            // 所以需要判断当前 profile 是否为 test, 不为 test 才执行 systemStartUp
            this.systemManagementService.systemStartUp();
        } else {
            log.info("Starting up development environment");
        }
        // 启动 handler
        filesystemEventHandler.startHandle();
    }
}
