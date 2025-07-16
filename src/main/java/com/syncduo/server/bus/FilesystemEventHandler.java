package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.internal.FilesystemEvent;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;


@Component
@Slf4j
public class FilesystemEventHandler {

    @Value("${syncduo.server.event.debounce.window:5}")
    private int DEBOUNCE_WINDOW;

    private final Map<Path, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final ThreadPoolTaskScheduler filesystemEventDebounceScheduler;

    private final RcloneFacadeService rcloneFacadeService;

    private final BlockingQueue<FilesystemEvent> filesystemEventQueue = new LinkedBlockingQueue<>(1000);

    @Autowired
    public FilesystemEventHandler(
            ThreadPoolTaskScheduler filesystemEventDebounceScheduler,
            RcloneFacadeService rcloneFacadeService) {
        this.filesystemEventDebounceScheduler = filesystemEventDebounceScheduler;
        this.rcloneFacadeService = rcloneFacadeService;
    }

    public void sendFileEvent(FilesystemEvent fileSystemEvent) throws SyncDuoException {
        log.debug("fileEvent: {}", fileSystemEvent);
        try {
            this.filesystemEventQueue.put(fileSystemEvent);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClassCastException | NullPointerException | IllegalArgumentException e) {
            throw new SyncDuoException(e);
        }
    }

    public void startHandle() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 从 queue 取 filesystem event
                    FilesystemEvent filesystemEvent = this.filesystemEventQueue.take();
                    // 过滤
                    if (ObjectUtils.isEmpty(filesystemEvent) ||
                            FileEventTypeEnum.FILE_DELETED.equals(filesystemEvent.getFileEventTypeEnum())) {
                        log.debug("filtered fileEvent: {}", filesystemEvent);
                        continue;
                    }
                    // debounce task
                    this.debounceTasks(filesystemEvent);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // good practice
                }
            }
        }, "filesystem-event-handler").start();
    }

    private void debounceTasks(FilesystemEvent filesystemEvent) {
        // Cancel existing scheduled task
        Path filePath = filesystemEvent.getFile();
        ScheduledFuture<?> previous = scheduledTasks.get(filePath);
        if (ObjectUtils.isNotEmpty(previous)) {
            previous.cancel(false);
        }
        // Schedule new task
        ScheduledFuture<?> future = filesystemEventDebounceScheduler.schedule(() -> {
            try {
                this.rcloneFacadeService.copyFile(filesystemEvent);
            } catch (SyncDuoException e) {
                log.error("triggerCopyFile failed. ", e);
            } finally {
                scheduledTasks.remove(filePath); // clean up
            }
        }, Instant.now().plusSeconds(DEBOUNCE_WINDOW));
        // add to map
        scheduledTasks.put(filePath, future);
        log.debug("debounce task added {}", filePath);
    }
}
