package com.syncduo.server.mq.consumer;

import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@Slf4j
public class SourceFolderHandler {

    @Value("${syncduo.server.event.polling.num:10}")
    private Integer pollingNum;

    private final SystemQueue systemQueue;

    private final FileService fileService;

    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final FileEventService fileEventService;

    @Autowired
    public SourceFolderHandler(SystemQueue systemQueue,
                               FileService fileService,
                               RootFolderService rootFolderService,
                               SyncFlowService syncFlowService,
                               FileEventService fileEventService) {
        this.systemQueue = systemQueue;
        this.fileService = fileService;
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileEventService = fileEventService;
    }

    // source watcher 触发
    // full scan source folder 触发
    // source 和 internal folder compare 触发
    @Scheduled(fixedDelayString = "${syncduo.server.message.polling.interval:5000}")
    private void handle() {
        for (int i = 0; i < pollingNum; i++) {
            FileEventDto fileEvent = systemQueue.pollSourceFolderEvent();
            if (ObjectUtils.isEmpty(fileEvent)) {
                break;
            }
            try {
                switch (fileEvent.getFileEventTypeEnum()) {
                    case FILE_CREATED -> this.onFileCreate(fileEvent);
                    case FILE_CHANGED -> this.onFileChange(fileEvent);
                    case FILE_DELETED -> this.onFileDelete(fileEvent);
                    default -> throw new SyncDuoException("文件夹的文件事件:%s 不识别".formatted(fileEvent));
                }
            } catch (SyncDuoException e) {
                log.error("文件夹的文件事件:%s 处理失败".formatted(fileEvent), e);
            }
        }
    }

    private void onFileCreate(FileEventDto fileEvent) throws SyncDuoException {
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderId(fileEvent.getRootFolderId());
        // create record
        FileEntity sourceFileEntity = this.fileService.fillFileEntityForCreate(
                fileEvent.getFile(),
                fileEvent.getRootFolderId(),
                sourceFolderEntity.getRootFolderFullPath()
        );
        this.fileService.createFileRecord(sourceFileEntity);
        // 记录 file event, 表示 source folder 发生的文件事件
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, sourceFileEntity);
        this.fileEventService.save(fileEventEntity);
        // hardlink file
        Pair<Path, FileEntity> fileAndEntityPair =
                this.addSourceFileToInternalFolder(sourceFolderEntity, sourceFileEntity);
        // 发送 event 到 content 队列
        this.systemQueue.sendFileEvent(FileEventDto.builder()
                        .file(fileAndEntityPair.getLeft())
                        .rootFolderId(fileAndEntityPair.getRight().getRootFolderId())
                        .fileEventTypeEnum(FileEventTypeEnum.FILE_CREATED)
                        .rootFolderTypeEnum(RootFolderTypeEnum.INTERNAL_FOLDER)
                        .destFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                        .build());
    }

    private void onFileChange(FileEventDto fileEvent) throws SyncDuoException {
        // 获取 file 及 rootFolderEntity
        Path file = fileEvent.getFile();
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderId(fileEvent.getRootFolderId());
        // 查数据库获取 sourceFileEntity
        FileEntity sourceFileEntity = this.fileService.getFileEntityFromFile(
                sourceFolderEntity.getRootFolderId(),
                sourceFolderEntity.getRootFolderFullPath(),
                file
        );
        // 更新  md5checksum,last_modified_time
        this.fileService.updateFileEntityByFile(sourceFileEntity, file);
        // 记录 file event, 表示 source folder 发生的文件事件
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, sourceFileEntity);
        this.fileEventService.save(fileEventEntity);
        // 更新 internal file entity
        Pair<Path, FileEntity> fileAndEntityPair = this.updateInternalFileFromSourceFileEntity(sourceFileEntity);
        // 发送 event 到 content 队列
        this.systemQueue.sendFileEvent(FileEventDto.builder()
                        .file(fileAndEntityPair.getLeft())
                        .rootFolderId(fileAndEntityPair.getRight().getRootFolderId())
                        .fileEventTypeEnum(FileEventTypeEnum.FILE_CHANGED)
                        .rootFolderTypeEnum(RootFolderTypeEnum.INTERNAL_FOLDER)
                        .destFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                        .build());
    }

    private void onFileDelete(FileEventDto fileEvent) throws SyncDuoException {
        // 获取 file 和 sourceFolderEntity
        Path file = fileEvent.getFile();
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderId(fileEvent.getRootFolderId());
        // 根据 fileEvent 查数据库获取 fileEntity
        FileEntity sourceFileEntity = this.fileService.getFileEntityFromFile(
                sourceFolderEntity.getRootFolderId(),
                sourceFolderEntity.getRootFolderFullPath(),
                file
        );
        // 更新 file_deleted=1
        sourceFileEntity.setFileDeleted(DeletedEnum.DELETED.getCode());
        // 更新数据库
        this.fileService.updateById(sourceFileEntity);
    }

    private Pair<Path, FileEntity> addSourceFileToInternalFolder(
            RootFolderEntity sourceFolderEntity,
            FileEntity sourceFileEntity) throws SyncDuoException {
        // 获取 source file 的 full path
        Path sourceFile = this.fileService.getFileFromFileEntity(
                sourceFolderEntity.getRootFolderFullPath(),
                sourceFileEntity
        );
        String sourceFileFullPath = sourceFile.toAbsolutePath().toString();
        // 获取 internal folder 信息
        SyncFlowEntity source2InternalSyncFlow =
                this.syncFlowService.getBySourceFolderId(sourceFileEntity.getRootFolderId());
        RootFolderEntity internalFolderEntity =
                this.rootFolderService.getByFolderId(source2InternalSyncFlow.getDestFolderId());
        // 拼接目标文件 full path
        String destFileFullPath = FileOperationUtils.concatePathString(
                internalFolderEntity.getRootFolderFullPath(),
                sourceFileEntity.getRelativePath(),
                sourceFileEntity.getFileName(),
                sourceFileEntity.getFileExtension()
        );
        // hardlink file
        Path internalFile = FileOperationUtils.hardlinkFile(sourceFileFullPath, destFileFullPath);
        // 填充 internal file entity
        FileEntity internalFileEntity = this.fileService.fillFileEntityForCreate(
                internalFile,
                internalFolderEntity.getRootFolderId(),
                internalFolderEntity.getRootFolderFullPath()
        );
        // 记录 internal file
        this.fileService.createFileRecord(internalFileEntity);
        return new ImmutablePair<>(internalFile, internalFileEntity);
    }

    private Pair<Path, FileEntity> updateInternalFileFromSourceFileEntity(FileEntity sourceFileEntity)
            throws SyncDuoException {
        // 获取 internal folder 信息
        SyncFlowEntity source2InternalSyncFlow =
                this.syncFlowService.getBySourceFolderId(sourceFileEntity.getRootFolderId());
        RootFolderEntity internalFolderEntity =
                this.rootFolderService.getByFolderId(source2InternalSyncFlow.getDestFolderId());
        // 获取 internal file entity
        FileEntity internalFileEntity = this.fileService.getDestFileEntityFromSourceEntity(
                internalFolderEntity.getRootFolderFullPath(),
                sourceFileEntity
        );
        // 获取 file
        Path internalFile = this.fileService.getFileFromFileEntity(
                internalFolderEntity.getRootFolderFullPath(),
                internalFileEntity);
        // 更新  md5checksum,last_modified_time
        this.fileService.updateFileEntityByFile(internalFileEntity, internalFile);
        return new ImmutablePair<>(internalFile, internalFileEntity);
    }

    private FileEventEntity fillFileEventEntityFromFileEvent(
            FileEventDto fileEvent, FileEntity fileEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileEvent)) {
            throw new SyncDuoException("创建文件事件记录失败, fileEventDto 为空");
        }
        FileEventEntity fileEventEntity = new FileEventEntity();
        fileEventEntity.setRootFolderId(fileEvent.getRootFolderId());
        fileEventEntity.setFileId(fileEntity.getFileId());
        fileEventEntity.setFileEventType(fileEvent.getFileEventTypeEnum().name());
        return fileEventEntity;
    }
}
