package com.syncduo.server.mq;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.dto.mq.FileMsgDto;
import com.syncduo.server.model.entity.RootFolderEntity;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class SystemQueue {
    private final ConcurrentLinkedQueue<FileEventDto> sourceFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileEventDto> internalFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileEventDto> contentFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileMsgDto> internalFileMsgQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileMsgDto> contentFileMsgQueue = new ConcurrentLinkedQueue<>();

    public void sendFileEvent(
            Path file,
            Long folderId,
            FileEventTypeEnum fileEventType,
            RootFolderTypeEnum rootFolderType) throws SyncDuoException {
        if (ObjectUtils.anyNull(file, folderId, fileEventType, rootFolderType)) {
            throw new SyncDuoException(
                    "发送 file event 失败, file, rootFolderEntity, fileEventType, rootFolderType 存在空值");
        }
        FileEventDto fileEvent = new FileEventDto();
        fileEvent.setFile(file);
        fileEvent.setRootFolderId(folderId);
        fileEvent.setFileEventType(fileEventType);
        fileEvent.setRootFolderTypeEnum(rootFolderType);
        switch (rootFolderType) {
            case SOURCE_FOLDER -> this.sourceFolderEventQueue.offer(fileEvent);
            case INTERNAL_FOLDER -> this.internalFolderEventQueue.offer(fileEvent);
            case CONTENT_FOLDER -> this.contentFolderEventQueue.offer(fileEvent);
            default -> throw new SyncDuoException("不支持的 rootFolderType %s".formatted(rootFolderType));
        }
    }

    public FileEventDto pollSourceFolderEvent() {
        return this.sourceFolderEventQueue.poll();
    }

    public FileMsgDto pollInternalFileMsg() {
        return this.internalFileMsgQueue.poll();
    }

    public FileMsgDto pollContentFileMsg() {
        return this.contentFileMsgQueue.poll();
    }

    public void pushInternalFileMsg(FileMsgDto fileMsgDto) {
        this.internalFileMsgQueue.offer(fileMsgDto);
    }

    public void pushContentFileMsg(FileMsgDto fileMsgDto) {
        this.contentFileMsgQueue.offer(fileMsgDto);
    }
}
