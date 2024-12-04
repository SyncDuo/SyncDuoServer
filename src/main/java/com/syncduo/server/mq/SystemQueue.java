package com.syncduo.server.mq;

import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class SystemQueue {
    private final ConcurrentLinkedQueue<FileEventDto> sourceFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileEventDto> contentFolderEventQueue = new ConcurrentLinkedQueue<>();

    // source folder watcher 调用
    // full scan 调用
    // internal file -> content file 消息体
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
        RootFolderTypeEnum destFolderTypeEnum = fileEvent.getDestFolderTypeEnum();
        switch (destFolderTypeEnum) {
            case SOURCE_FOLDER,INTERNAL_FOLDER -> this.sourceFolderEventQueue.offer(fileEvent);
            case CONTENT_FOLDER -> this.contentFolderEventQueue.offer(fileEvent);
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
