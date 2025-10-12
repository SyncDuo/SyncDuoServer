package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.internal.FilesystemEvent;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.bussiness.SystemManagementService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;


@Component
@Slf4j
public class FilesystemEventHandler {

    @Value("${syncduo.server.system.eventDebounceWindowSec:5}")
    private int DEBOUNCE_WINDOW;

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final SystemManagementService systemManagementService;

    private final BlockingQueue<FilesystemEvent> filesystemEventQueue;

    @Autowired
    public FilesystemEventHandler(
            DebounceService debounceService,
            SystemManagementService systemManagementService,
            FolderWatcher folderWatcher) {
        this.moduleDebounceService = debounceService.forModule(FilesystemEventHandler.class.getSimpleName());
        this.systemManagementService = systemManagementService;
        this.filesystemEventQueue = folderWatcher.getFilesystemEventQueue();
    }

    public void startHandle() {
        // 起一个固定线程, 轮询 queue
        new Thread(() -> {
            log.info("Filesystem Event Handler Start");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 从 queue 取 filesystem event
                    FilesystemEvent filesystemEvent = this.filesystemEventQueue.take();
                    log.debug("receive fileEvent: {}", filesystemEvent);
                    // 错误的 filesystem event 过滤
                    if (ObjectUtils.anyNull(
                            filesystemEvent,
                            filesystemEvent.getFolder(),
                            filesystemEvent.getFile(),
                            filesystemEvent.getFileEventTypeEnum())) {
                        log.warn("receive invalid filesystem event: {}", filesystemEvent);
                    }
                    // 不处理删除事件, 过滤
                    if (FileEventTypeEnum.FILE_DELETED.equals(filesystemEvent.getFileEventTypeEnum())) {
                        log.debug("filtered fileEvent: {}", filesystemEvent);
                        continue;
                    }
                    // DEBOUNCE_WINDOW 内同个文件没有新的事件, 则执行 copyFile
                    this.moduleDebounceService.debounce(
                            filesystemEvent.getFile().toAbsolutePath().toString(),
                            () -> this.systemManagementService.copyFile(filesystemEvent),
                            DEBOUNCE_WINDOW
                    );
                    log.debug("debounced fileEvent: {}", filesystemEvent);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // good practice
                }
            }
        }, "Filesystem-Event-Handler").start();
    }
}
