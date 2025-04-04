package com.syncduo.server.configuration;


import com.syncduo.server.bus.handler.DownstreamHandler;
import com.syncduo.server.bus.handler.FileSystemEventHandler;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.service.bussiness.impl.SystemConfigService;
import com.syncduo.server.service.facade.SystemManagementService;
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

    private final FileSystemEventHandler fileSystemEventHandler;

    private final DownstreamHandler downstreamHandler;

    private final SystemConfigService systemConfigService;

    @Autowired
    public ApplicationLifeCycleConfig(
            SystemManagementService systemManagementService,
            FileSystemEventHandler fileSystemEventHandler,
            DownstreamHandler downstreamHandler,
            SystemConfigService systemConfigService) {
        this.systemManagementService = systemManagementService;
        this.fileSystemEventHandler = fileSystemEventHandler;
        this.downstreamHandler = downstreamHandler;
        this.systemConfigService = systemConfigService;
    }

    @PostConstruct
    public void startUp() throws SyncDuoException {
        // 系统启动扫描
        if ("prod".equals(activeProfile)) {
            // 获取系统设置
            this.systemConfigService.getSystemConfig();
            // 检查全部 sync-flow 是否同步
            // @PostConstruct 在 @BeforeEach 前面, 会导致在 "旧的folder" 上添加 watcher
            // "旧的folder" 在 @BeforeEach 中删除, 后续触发的事件会发生异常
            // 所以需要判断当前 profile 是否为 test, 不为 test 才执行 systemStartUp
            this.systemManagementService.systemStartUp();
        }
        // 启动 handler
        fileSystemEventHandler.startHandle();
        downstreamHandler.startHandle();
    }
}
