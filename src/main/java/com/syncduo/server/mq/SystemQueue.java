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

    // source folder watcher
    // content folder watcher
    // source folder full scan
    // dest folder full scan
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
        log.info("file event {}", fileEvent);
        RootFolderTypeEnum rootFolderType = fileEvent.getRootFolderTypeEnum();
        RootFolderTypeEnum destFolderTypeEnum = fileEvent.getDestFolderTypeEnum();
        switch (destFolderTypeEnum) {
            // source -> source, source -> internal 都会增加 event count
            // 因为两种流向的事件都会导致同一条 sync-flow 为 NOT_SYNC
            // 但是 FILE_DELETE 不会导致 NOT_SYNC
            // 因为 source folder delete 是正常的, 整个系统设计就是 ignore source folder file delete
            // 然后 internal folder delete 是正常的, 是用户自行选择删除无用的 file
            // 所以 FILE_DELETE 事件发生在 source 和 internal folder 不会增加 event count
            case SOURCE_FOLDER, INTERNAL_FOLDER -> {
                this.sourceFolderEventQueue.offer(fileEvent);
                if (fileEvent.getFileEventTypeEnum().equals(FileEventTypeEnum.FILE_DELETED)) {
                    break;
                }
                this.syncFlowService.incrSource2InternalCount(fileEvent.getRootFolderId());
            }
            case CONTENT_FOLDER -> {
                // 传递事件
                this.contentFolderEventQueue.offer(fileEvent);
                // 如果是 internal -> content, 则增加 pending event count
                if (rootFolderType.equals(RootFolderTypeEnum.INTERNAL_FOLDER)) {
                    this.syncFlowService.incrInternal2ContentCount(fileEvent.getRootFolderId());
                }
            }
            default -> throw new SyncDuoException("不支持的 rootFolderType %s".formatted(destFolderTypeEnum));
        }

    }

    public FileEventDto pollSourceFolderEvent() {
        return this.sourceFolderEventQueue.poll();
    }

    public FileEventDto pollContentFolderEvent() {
        return this.contentFolderEventQueue.poll();
    }

    public void pushContentFileEvent(FileEventDto fileEvent) {
        this.contentFolderEventQueue.offer(fileEvent);
    }
}
