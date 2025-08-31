package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.internal.FilesystemEvent;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.*;


@Component
@Slf4j
public class FilesystemEventHandler {

    @Value("${syncduo.server.system.event.debounce.window.sec:5}")
    private int DEBOUNCE_WINDOW;

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final RcloneFacadeService rcloneFacadeService;

    private final BlockingQueue<FilesystemEvent> filesystemEventQueue = new LinkedBlockingQueue<>(1000);

    @Autowired
    public FilesystemEventHandler(
            DebounceService debounceService,
            RcloneFacadeService rcloneFacadeService) {
        this.moduleDebounceService = debounceService.forModule(FilesystemEventHandler.class.getSimpleName());;
        this.rcloneFacadeService = rcloneFacadeService;
    }

    public void sendFileEvent(FilesystemEvent fileSystemEvent) throws SyncDuoException {
        log.debug("fileEvent: {}", fileSystemEvent);
        try {
            this.filesystemEventQueue.put(fileSystemEvent);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClassCastException | NullPointerException | IllegalArgumentException e) {
            throw new SyncDuoException("sendFileEvent failed. ", e);
        }
    }

    public void startHandle() {
        new Thread(() -> {
            log.info("Filesystem Event Handler Start");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 从 queue 取 filesystem event
                    FilesystemEvent filesystemEvent = this.filesystemEventQueue.take();
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
                    // debounce task, DEBOUNCE_WINDOW 内同个文件没有新的事件, 则执行 copyFile
                    this.moduleDebounceService.debounce(
                            filesystemEvent.getFile().toAbsolutePath().toString(),
                            () -> this.rcloneFacadeService.copyFile(filesystemEvent),
                            DEBOUNCE_WINDOW
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // good practice
                }
            }
        }, "Filesystem-Event-Handler").start();
    }
}
