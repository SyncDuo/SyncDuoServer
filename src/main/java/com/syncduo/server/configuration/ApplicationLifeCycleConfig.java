package com.syncduo.server.configuration;


import com.syncduo.server.bus.FileEventHandler;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.service.facade.FileOperationService;
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

    private final FileOperationService fileOperationService;

    private final FileEventHandler fileEventHandler;

    @Autowired
    public ApplicationLifeCycleConfig(
            FileOperationService fileOperationService,
            FileEventHandler fileEventHandler) {
        this.fileOperationService = fileOperationService;
        this.fileEventHandler = fileEventHandler;
    }

    @PostConstruct
    public void startUp() throws SyncDuoException {
        if ("prod".equals(activeProfile)) {
            // 检查全部 sync-flow 是否同步
            // @PostConstruct 在 @BeforeEach 前面, 会导致在 "旧的folder" 上添加 watcher
            // "旧的folder" 在 @BeforeEach 中删除, 后续触发的事件会发生异常
            // 所以需要判断当前 profile 是否为 test, 不为 test 才执行 systemStartUp
            this.fileOperationService.systemStartUp();
        }
        // 启动 handler
        fileEventHandler.startHandle();
    }
}
