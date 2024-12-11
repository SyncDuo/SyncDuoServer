package com.syncduo.server.configuration;


import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mq.consumer.ContentFolderHandler;
import com.syncduo.server.mq.consumer.SourceFolderHandler;
import com.syncduo.server.service.impl.AdvancedFileOpService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationLifeCycleConfig {

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
        // 检查全部 sync-flow 是否同步
        // @PostConstruct 在 @BeforeEach 前面, 会导致在 "旧的folder" 上添加 watcher
        // "旧的folder" 在 @BeforeEach 中删除, 后续触发的事件会发生异常
        // 具体为不存在的 rootFolderId 事件频发发出, 这些异常都可以忽略
        this.advancedFileOpService.systemStartUp();
        // 启动 handler
        this.sourceFolderHandler.startHandle();
        this.contentFolderHandler.startHandle();
    }
}
