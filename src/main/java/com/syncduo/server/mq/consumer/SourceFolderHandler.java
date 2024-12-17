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
import com.syncduo.server.mq.FileAccessValidator;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.service.impl.FileEventService;
import com.syncduo.server.service.impl.FileService;
import com.syncduo.server.service.impl.RootFolderService;
import com.syncduo.server.service.impl.SyncFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@Slf4j
public class SourceFolderHandler implements DisposableBean {

    private final SystemQueue systemQueue;

    private final FileService fileService;

    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final FileEventService fileEventService;

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final FileAccessValidator fileAccessValidator;

    private volatile boolean RUNNING = true;  // Flag to control the event loop

    @Autowired
    public SourceFolderHandler(SystemQueue systemQueue,
                               FileService fileService,
                               RootFolderService rootFolderService,
                               SyncFlowService syncFlowService,
                               FileEventService fileEventService,
                               ThreadPoolTaskExecutor threadPoolTaskExecutor,
                               FileAccessValidator fileAccessValidator) {
        this.systemQueue = systemQueue;
        this.fileService = fileService;
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileEventService = fileEventService;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.fileAccessValidator = fileAccessValidator;
    }

    // source watcher 触发
    // full scan source folder, content folder 触发
    // source 和 internal folder compare 触发
    @Async("threadPoolTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            FileEventDto fileEvent = systemQueue.pollSourceFolderEvent();
            if (ObjectUtils.isEmpty(fileEvent) || fileAccessValidator.isFileEventValid(fileEvent.getRootFolderId())) {
                continue;
            }
            this.threadPoolTaskExecutor.submit(() -> {
                try {
                    switch (fileEvent.getFileEventTypeEnum()) {
                        // 每一种事件其实都包含了两种流向
                        // source -> internal, internal -> internal
                        case FILE_CREATED -> this.onFileCreate(fileEvent);
                        case FILE_CHANGED -> this.onFileChange(fileEvent);
                        case FILE_DELETED -> this.onFileDelete(fileEvent);
                        default -> throw new SyncDuoException("文件夹的文件事件:%s 不识别".formatted(fileEvent));
                    }
                } catch (SyncDuoException e) {
                    log.error("文件夹的文件事件:{} 处理失败", fileEvent, e);
                }
            });
        }
    }

    private void onFileCreate(FileEventDto fileEvent) throws SyncDuoException {
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderId(fileEvent.getRootFolderId());
        if (ObjectUtils.isEmpty(sourceFolderEntity)) {
            throw new SyncDuoException("sourceFolderEntity 为空");
        }
        // 查看是否有重复的文件
        FileEntity sourceFileEntity = this.fileService.getFileEntityFromFile(
                sourceFolderEntity.getRootFolderId(),
                sourceFolderEntity.getRootFolderFullPath(),
                fileEvent.getFile()
        );
        if (ObjectUtils.isEmpty(sourceFileEntity)) {
            // create record
            sourceFileEntity = this.fileService.fillFileEntityForCreate(
                    fileEvent.getFile(),
                    fileEvent.getRootFolderId(),
                    sourceFolderEntity.getRootFolderFullPath()
            );
            this.fileService.createFileRecord(sourceFileEntity);
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
        // 减少 source -> internal pending event
        this.syncFlowService.decrSource2InternalCount(sourceFolderEntity.getRootFolderId());
        // 记录 file event, 表示 source folder 发生的文件事件
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, sourceFileEntity);
        this.fileEventService.save(fileEventEntity);
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
        // 更新  md5 checksum, last_modified_time
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
        // 减少 source -> internal pending event
        this.syncFlowService.decrSource2InternalCount(sourceFolderEntity.getRootFolderId());
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
        // 幂等
        if (ObjectUtils.isEmpty(sourceFileEntity)) {
            return;
        }
        // 更新 file_deleted=1
        sourceFileEntity.setFileDeleted(DeletedEnum.DELETED.getCode());
        // 更新数据库
        this.fileService.updateById(sourceFileEntity);
    }

    // Pair<>(internalFile, internalFileEntity);
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
                this.syncFlowService.getSourceSyncFlowByFolderId(sourceFileEntity.getRootFolderId());
        RootFolderEntity internalFolderEntity =
                this.rootFolderService.getByFolderId(source2InternalSyncFlow.getDestFolderId());
        // 拼接目标文件 full path
        String destFileFullPath = this.fileService.concatPathStringFromFolderAndFile(
                internalFolderEntity.getRootFolderFullPath(),
                sourceFileEntity
        );
        // hardlink file
        Path internalFile = fileAccessValidator.hardlinkFile(
                sourceFolderEntity.getRootFolderId(),
                sourceFileFullPath,
                internalFolderEntity.getRootFolderId(),
                destFileFullPath);
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
                this.syncFlowService.getSourceSyncFlowByFolderId(sourceFileEntity.getRootFolderId());
        RootFolderEntity internalFolderEntity =
                this.rootFolderService.getByFolderId(source2InternalSyncFlow.getDestFolderId());
        // 获取 internal file entity
        FileEntity internalFileEntity = this.fileService.getInternalFileEntityFromSourceEntity(
                internalFolderEntity.getRootFolderId(),
                sourceFileEntity
        );
        // 获取 file
        Path internalFile = this.fileService.getFileFromFileEntity(
                internalFolderEntity.getRootFolderFullPath(),
                internalFileEntity);
        // 更新  md5 checksum, last_modified_time
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

    @Override
    public void destroy() {
        log.info("stop source handler");
        RUNNING = false;
    }
}
