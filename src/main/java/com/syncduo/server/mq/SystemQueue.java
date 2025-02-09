package com.syncduo.server.mq;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.service.impl.SyncFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Slf4j
public class SystemQueue {
    private final ConcurrentLinkedQueue<FileEventDto> sourceFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileEventDto> contentFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final SyncFlowService syncFlowService;

    @Autowired
    public SystemQueue(SyncFlowService syncFlowService) {
        this.syncFlowService = syncFlowService;
    }

    // source folder watcher : source to source, internal -> content,
    // content folder watcher : content -> content,
    // source folder full scan : source to source, internal -> content,
    // dest folder full scan : content -> content
    public void sendFileEvent(FileEventDto fileEvent) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileEvent)) {
            throw new SyncDuoException("发送 file event 失败, file event 为空");
        }
        if (ObjectUtils.anyNull(
                fileEvent.getFile(),
                fileEvent.getRootFolderId(),
                fileEvent.getFileEventTypeEnum(),
                fileEvent.getRootFolderTypeEnum(),
                fileEvent.getDestFolderTypeEnum())) {
            throw new SyncDuoException("发送 file event 失败, fileEvent %s".formatted(fileEvent) +
                    "file, rootFolderId, fileEventType, rootFolderType, destFolderTypeEnum 存在空值");
        }
        log.debug("file event {}", fileEvent);
        this.sourceFolderEventQueue.offer(fileEvent);
        // 只有两种事件会发出
        // 1. source -> source, 此类事件发出表示 source->internal 不同步
        // 2. internal -> content, 此类事件发出表示 internal->content 不同步
        // * 其中 1 如果是 FILE_DELETE 则排除
        RootFolderTypeEnum rootFolderType = fileEvent.getRootFolderTypeEnum();
        RootFolderTypeEnum destFolderTypeEnum = fileEvent.getDestFolderTypeEnum();
        switch (rootFolderType) {
            case SOURCE_FOLDER -> {
                if (destFolderTypeEnum.equals(RootFolderTypeEnum.SOURCE_FOLDER) &&
                        !fileEvent.getFileEventTypeEnum().equals(FileEventTypeEnum.FILE_DELETED)) {
                    this.syncFlowService.incrSource2InternalEventCount(fileEvent.getRootFolderId());
                }
            }
            case INTERNAL_FOLDER -> {
                if (destFolderTypeEnum.equals(RootFolderTypeEnum.CONTENT_FOLDER)) {
                    this.syncFlowService.incrInternal2ContentEventCount(fileEvent.getRootFolderId());
                }
            }
        }
    }

    public FileEventDto pollSourceFolderEvent() {
        return this.sourceFolderEventQueue.poll();
    }

    public FileEventDto pollContentFolderEvent() {
        return this.contentFolderEventQueue.poll();
    }
}
