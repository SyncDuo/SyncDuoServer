package com.syncduo.server.configuration;


import com.syncduo.server.bus.handler.DownstreamHandler;
import com.syncduo.server.bus.handler.FileSystemEventHandler;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.service.bussiness.impl.SystemConfigService;
import com.syncduo.server.service.facade.SystemManagementService;
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

    private final FileSystemEventHandler fileSystemEventHandler;

    private final DownstreamHandler downstreamHandler;

    private final SystemConfigService systemConfigService;

    private final ThreadPoolConfig threadPoolConfig;

    @Autowired
    public ApplicationLifeCycleConfig(
            SystemManagementService systemManagementService,
            FileSystemEventHandler fileSystemEventHandler,
            DownstreamHandler downstreamHandler,
            SystemConfigService systemConfigService,
            ThreadPoolConfig threadPoolConfig) {
        this.systemManagementService = systemManagementService;
        this.fileSystemEventHandler = fileSystemEventHandler;
        this.downstreamHandler = downstreamHandler;
        this.systemConfigService = systemConfigService;
        this.threadPoolConfig = threadPoolConfig;
    }

    @PostConstruct
    public void startUp() throws SyncDuoException {
        // 系统启动扫描
        if ("prod".equals(activeProfile)) {
            log.info("Starting up production environment");
            // 获取系统设置
            SystemConfigEntity systemConfig = this.systemConfigService.getSystemConfig();
            this.setupConfigAtFirstTime(systemConfig);
            // 检查全部 sync-flow 是否同步
            // @PostConstruct 在 @BeforeEach 前面, 会导致在 "旧的folder" 上添加 watcher
            // "旧的folder" 在 @BeforeEach 中删除, 后续触发的事件会发生异常
            // 所以需要判断当前 profile 是否为 test, 不为 test 才执行 systemStartUp
            this.systemManagementService.systemStartUp();
        } else {
            log.info("Starting up development environment");
        }
        // 启动 handler
        fileSystemEventHandler.startHandle();
        downstreamHandler.startHandle();
    }

    private void setupConfigAtFirstTime(SystemConfigEntity systemConfigEntity) throws SyncDuoException {
        int handlerMinThreads = 5;
        int handlerMaxThreads = 5;
        if (ObjectUtils.isNotEmpty(systemConfigEntity)) {
            handlerMinThreads = systemConfigEntity.getHandlerMinThreads();
            handlerMaxThreads = systemConfigEntity.getHandlerMaxThreads();
        }
        this.threadPoolConfig.changeMinAndMaxThreadsNum(handlerMinThreads, handlerMaxThreads);
    }
}
