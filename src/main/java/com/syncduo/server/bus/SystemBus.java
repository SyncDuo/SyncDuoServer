package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileSystemEvent;
import com.syncduo.server.model.internal.RefilterEvent;
import com.syncduo.server.service.bussiness.impl.SyncFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Slf4j
public class SystemBus {

    private final SyncFlowService syncFlowService;

    private final ConcurrentLinkedQueue<FileSystemEvent> fileSystemEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<DownStreamEvent> downStreamEventQueue = new ConcurrentLinkedQueue<>();

    @Autowired
    public SystemBus(SyncFlowService syncFlowService) {
        this.syncFlowService = syncFlowService;
    }

    public void sendFileEvent(FileSystemEvent fileSystemEvent) throws SyncDuoException {
        log.debug("fileEvent: {}", fileSystemEvent);
        this.fileSystemEventQueue.add(fileSystemEvent);
    }

    public FileSystemEvent getFileEvent() {
        return this.fileSystemEventQueue.poll();
    }

    public void sendDownStreamEvent(DownStreamEvent downStreamEvent) throws SyncDuoException {
        log.debug("downStreamEvent: {}", downStreamEvent);
        this.downStreamEventQueue.add(downStreamEvent);
        this.incrSyncFlowPendingEventCount(downStreamEvent);
    }

    public DownStreamEvent getDownStreamEvent() {
        return this.downStreamEventQueue.poll();
    }

    public void decrSyncFlowPendingEventCount(DownStreamEvent downStreamEvent) throws SyncDuoException {
        if (FileEventTypeEnum.FILE_REFILTER_CREATED.equals(downStreamEvent.getFileEventTypeEnum()) ||
                FileEventTypeEnum.FILE_REFILTER_DELETED.equals(downStreamEvent.getFileEventTypeEnum())) {
            // refilter event, 根据单条 syncflow 增加 pending event count, 因为不是从 watcher 触发的
            this.syncFlowService.decrPendingEventCount(downStreamEvent.getSyncFlowEntity());
        } else {
            // 其他 event, 批量增加 pending event count, 因为是从 watcher 触发
            syncFlowService.decrPendingEventCount(downStreamEvent.getFolderEntity().getFolderId());
        }
    }

    private void incrSyncFlowPendingEventCount(DownStreamEvent downStreamEvent) throws SyncDuoException {
        if (FileEventTypeEnum.FILE_REFILTER_CREATED.equals(downStreamEvent.getFileEventTypeEnum()) ||
                FileEventTypeEnum.FILE_REFILTER_DELETED.equals(downStreamEvent.getFileEventTypeEnum())) {
            // refilter event, 根据单条 syncflow 增加 pending event count, 因为不是从 watcher 触发的
            this.syncFlowService.addPendingEventCount(downStreamEvent.getSyncFlowEntity());
        } else {
            // 其他 event, 批量增加 pending event count, 因为是从 watcher 触发
            syncFlowService.addPendingEventCount(downStreamEvent.getFolderEntity().getFolderId());
        }
    }
}
