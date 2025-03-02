package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileEvent;
import com.syncduo.server.service.bussiness.impl.SyncFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Slf4j
public class SystemBus {

    private final SyncFlowService syncFlowService;

    private final ConcurrentLinkedQueue<FileEvent> fileEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<DownStreamEvent> downStreamEventQueue = new ConcurrentLinkedQueue<>();

    @Autowired
    public SystemBus(SyncFlowService syncFlowService) {
        this.syncFlowService = syncFlowService;
    }

    public void sendFileEvent(FileEvent fileEvent) throws SyncDuoException {
        log.debug("fileEvent: {}", fileEvent);
        this.fileEventQueue.add(fileEvent);
        // 增加 pending event count
        syncFlowService.addPendingEventCount(fileEvent.getFolderId());
    }

    public FileEvent getFileEvent() {
        return this.fileEventQueue.poll();
    }

    public void sendDownStreamEvent(DownStreamEvent downStreamEvent) throws SyncDuoException {
        log.debug("downStreamEvent: {}", downStreamEvent);
        this.downStreamEventQueue.add(downStreamEvent);
    }

    public DownStreamEvent getDownStreamEvent() {
        return this.downStreamEventQueue.poll();
    }
}
