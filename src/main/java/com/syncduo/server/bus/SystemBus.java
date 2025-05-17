package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileSystemEvent;
import com.syncduo.server.service.cache.SyncFlowServiceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Slf4j
public class SystemBus {

    private final SyncFlowServiceCache syncFlowServiceCache;

    private final ConcurrentLinkedQueue<FileSystemEvent> fileSystemEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<DownStreamEvent> downStreamEventQueue = new ConcurrentLinkedQueue<>();

    @Autowired
    public SystemBus(SyncFlowServiceCache syncFlowServiceCache) {
        this.syncFlowServiceCache = syncFlowServiceCache;
    }

    public void sendFileEvent(FileSystemEvent fileSystemEvent) throws SyncDuoException {
        log.debug("fileEvent: {}", fileSystemEvent);
        this.fileSystemEventQueue.offer(fileSystemEvent);
    }

    public FileSystemEvent getFileEvent() {
        return this.fileSystemEventQueue.poll();
    }

    public void sendDownStreamEvent(DownStreamEvent downStreamEvent) throws SyncDuoException {
        log.debug("downStreamEvent: {}", downStreamEvent);
        this.downStreamEventQueue.offer(downStreamEvent);
        this.incrSyncFlowPendingEventCount(downStreamEvent);
    }

    public DownStreamEvent getDownStreamEvent() {
        return this.downStreamEventQueue.poll();
    }

    public void decrSyncFlowPendingEventCount(DownStreamEvent downStreamEvent) throws SyncDuoException {
        if (FileEventTypeEnum.FILE_REFILTER_CREATED.equals(downStreamEvent.getFileEventTypeEnum()) ||
            FileEventTypeEnum.FILE_REFILTER_DELETED.equals(downStreamEvent.getFileEventTypeEnum()) ||
            FileEventTypeEnum.DB_FILE_RETRIEVE.equals(downStreamEvent.getFileEventTypeEnum())) {
            // 根据单条 syncflow 增加 pending event count, 因为不是从 watcher 触发的
            this.syncFlowServiceCache.decrPendingEventCount(downStreamEvent.getSyncFlowEntity());
        } else {
            // 其他 event, 批量增加 pending event count, 因为是从 watcher 触发
            this.syncFlowServiceCache.decrPendingEventCount(downStreamEvent.getFolderEntity().getFolderId());
        }
    }

    private void incrSyncFlowPendingEventCount(DownStreamEvent downStreamEvent) throws SyncDuoException {
        if (FileEventTypeEnum.FILE_REFILTER_CREATED.equals(downStreamEvent.getFileEventTypeEnum()) ||
            FileEventTypeEnum.FILE_REFILTER_DELETED.equals(downStreamEvent.getFileEventTypeEnum()) ||
            FileEventTypeEnum.DB_FILE_RETRIEVE.equals(downStreamEvent.getFileEventTypeEnum())) {
            // 根据单条 syncflow 增加 pending event count, 因为不是从 watcher 触发的
            this.syncFlowServiceCache.addPendingEventCount(downStreamEvent.getSyncFlowEntity());
        } else {
            // 其他 event, 批量增加 pending event count, 因为是从 watcher 触发
            this.syncFlowServiceCache.addPendingEventCount(downStreamEvent.getFolderEntity().getFolderId());
        }
    }
}
