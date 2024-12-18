package com.syncduo.server.configuration;


import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mq.consumer.ContentFolderHandler;
import com.syncduo.server.mq.consumer.SourceFolderHandler;
import com.syncduo.server.service.impl.AdvancedFileOpService;
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

    private final AdvancedFileOpService advancedFileOpService;

    private final SourceFolderHandler sourceFolderHandler;

    private final ContentFolderHandler contentFolderHandler;

    @Autowired
    public ApplicationLifeCycleConfig(
            AdvancedFileOpService advancedFileOpService,
            SourceFolderHandler sourceFolderHandler,
            ContentFolderHandler contentFolderHandler) {
        this.advancedFileOpService = advancedFileOpService;
        this.sourceFolderHandler = sourceFolderHandler;
        this.contentFolderHandler = contentFolderHandler;
    }

    @PostConstruct
    public void startUp() throws SyncDuoException {
        if ("prod".equals(activeProfile)) {
            // 检查全部 sync-flow 是否同步
            // @PostConstruct 在 @BeforeEach 前面, 会导致在 "旧的folder" 上添加 watcher
            // "旧的folder" 在 @BeforeEach 中删除, 后续触发的事件会发生异常
            // 所以需要判断当前 profile 是否为 test, 不为 test 才执行 systemStartUp
            this.advancedFileOpService.systemStartUp();
        }
        // 启动 handler
        this.sourceFolderHandler.startHandle();
        this.contentFolderHandler.startHandle();
    }
}
