package com.syncduo.server.mq.consumer;

import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.FileAccessValidator;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ContentFolderHandler implements DisposableBean {
    private final SystemQueue systemQueue;

    private final FileService fileService;

    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final SyncSettingService syncSettingService;

    private final FileEventService fileEventService;

    private final FileAccessValidator fileAccessValidator;

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private volatile boolean RUNNING = true;

    public ContentFolderHandler(
            SystemQueue systemQueue,
            FileService fileService,
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            SyncSettingService syncSettingService,
            FileEventService fileEventService,
            FileAccessValidator fileAccessValidator,
            ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.systemQueue = systemQueue;
        this.fileService = fileService;
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.syncSettingService = syncSettingService;
        this.fileEventService = fileEventService;
        this.fileAccessValidator = fileAccessValidator;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    // full scan content folder 触发. root folder type = content folder, 则不需要 file copy
    // internal folder 触发, root folder type = internal folder, 需要 file copy
    // internal 和 content folder compare 触发, root folder type = internal folder, 需要 file copy
    @Async("threadPoolTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            FileEventDto fileEvent = this.systemQueue.pollContentFolderEvent();
            if (ObjectUtils.isEmpty(fileEvent)|| !fileAccessValidator.isFileEventValid(fileEvent.getRootFolderId())) {
                continue;
            }
            this.threadPoolTaskExecutor.submit(() -> {
                try {
                    switch (fileEvent.getFileEventTypeEnum()) {
                        case FILE_CREATED -> {
                            switch (fileEvent.getRootFolderTypeEnum()) {
                                case INTERNAL_FOLDER -> this.onFileCreateFromInternalFolder(fileEvent);
                                case CONTENT_FOLDER -> this.onFileCreateFromContentFolder(fileEvent);
                            }
                        }
                        case FILE_CHANGED -> {
                            switch (fileEvent.getRootFolderTypeEnum()) {
                                case INTERNAL_FOLDER -> this.onFileChangeFromInternalFolder(fileEvent);
                                case CONTENT_FOLDER -> this.onFileDeSynced(fileEvent);
                            }
                        }
                        case FILE_DESYNCED -> this.onFileDeSynced(fileEvent);
                        case FILE_DELETED -> this.onFileDeleteFromContentFolder(fileEvent);
                        default ->
                                throw new SyncDuoException("content 文件夹的文件事件:%s 不识别".formatted(fileEvent));
                    }
                } catch (SyncDuoException e) {
                    log.error("content 文件夹的文件事件:{} 处理失败", fileEvent, e);
                }
            });
        }
    }

    private void onFileCreateFromContentFolder(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(rootFolderId);
        // 检查 file 是否已创建
        FileEntity contentFileEntity = this.fileService.getFileEntityFromFile(
                rootFolderEntity.getRootFolderId(),
                rootFolderEntity.getRootFolderFullPath(),
                file
        );
        if (ObjectUtils.isEmpty(contentFileEntity)) {
            contentFileEntity = this.fileService.fillFileEntityForCreate(
                    file,
                    rootFolderId,
                    rootFolderEntity.getRootFolderFullPath());
            // file created from content folder, is desynced already
            contentFileEntity.setFileDesync(FileDesyncEnum.FILE_DESYNC.getCode());
            this.fileService.createFileRecord(contentFileEntity);
        }
        // 记录 file event
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, contentFileEntity);
        this.fileEventService.save(fileEventEntity);
    }

    private void onFileCreateFromInternalFolder(FileEventDto fileEvent) throws SyncDuoException {
        // 获取 file 和 对应的 fileEntity
        Path internalFile = fileEvent.getFile();
        RootFolderEntity internalFolderEntity = this.rootFolderService.getByFolderId(fileEvent.getRootFolderId());
        FileEntity internalFileEntity = this.fileService.getFileEntityFromFile(
                internalFolderEntity.getRootFolderId(),
                internalFolderEntity.getRootFolderFullPath(),
                internalFile
        );
        // 根据 internalFolderId -> sync-flow -> contentFolderEntity
        List<SyncFlowEntity> syncFlowList =
                this.syncFlowService.getInternal2ContentSyncFlowListByFolderId(fileEvent.getRootFolderId());
        if (CollectionUtils.isEmpty(syncFlowList)) {
            throw new SyncDuoException(
                    "创建 content file 失败,没有找到对应的 sync flow. fileEvent 是 %s".formatted(fileEvent));
        }
        // 每一个 content folder 执行一样的操作
        for (SyncFlowEntity syncFlowEntity : syncFlowList) {
            // 获取 content folder entity
            RootFolderEntity contentFolderEntity =
                    this.rootFolderService.getByFolderId(syncFlowEntity.getDestFolderId());
            Long syncFlowId = syncFlowEntity.getSyncFlowId();
            // 判断是否过滤
            // 每个文件都不管历史有没有过滤, 都要从重新走一遍, 就是想让 filter 修改后可以生效
            if (this.syncSettingService.isFilter(syncFlowId, internalFile)) {
                // 减少 pending event count
                this.syncFlowService.decrInternal2ContentEventCount(syncFlowEntity.getDestFolderId());
                continue;
            }
            boolean flattenFolder = this.syncSettingService.isFlattenFolder(syncFlowId);
            String contentFileFullPath;
            // internal -> content file, 如果是 flatten
            // 则 content file entity 的 file_name = internal file entity 的 uuid4
            // 且 content file entity 的 relative path 为 "/"
            if (flattenFolder) {
                // 如果不使用原来的文件夹结构, 则 file copy with uuid4 name, 且 relative path 为空
                contentFileFullPath = this.fileService.concatContentFilePathFlattenFolder(
                        contentFolderEntity.getRootFolderFullPath(),
                        internalFileEntity
                );
            } else {
                // 如果镜像复制, 则 file copy
                contentFileFullPath = this.fileService.concatPathStringFromFolderAndFile(
                        contentFolderEntity.getRootFolderFullPath(),
                        internalFileEntity
                );
            }
            // 如果 contentFileFullPath 已存在文件, 说明 create file event 重复了, 则直接返回
            if (FileOperationUtils.isFilePathExist(contentFileFullPath)) {
                // 减少 pending event count
                this.syncFlowService.decrInternal2ContentEventCount(syncFlowEntity.getDestFolderId());
                continue;
            }
            // file copy
            Path contentFile = fileAccessValidator.copyFile(
                    internalFolderEntity.getRootFolderId(),
                    internalFile.toAbsolutePath().toString(),
                    contentFolderEntity.getRootFolderId(),
                    contentFileFullPath);
            // create record
            FileEntity contentFileEntity = this.fileService.fillFileEntityForCreate(
                    contentFile,
                    contentFolderEntity.getRootFolderId(),
                    contentFolderEntity.getRootFolderFullPath()
            );
            this.fileService.createFileRecord(contentFileEntity);
            // 减少 pending event count
            this.syncFlowService.decrInternal2ContentEventCount(syncFlowEntity.getDestFolderId());
            // 记录 file event
            FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, contentFileEntity);
            this.fileEventService.save(fileEventEntity);
        }
    }

    private void onFileChangeFromInternalFolder(FileEventDto fileEvent) throws SyncDuoException {
        // 获取 file 和 对应的 fileEntity
        Path internalFile = fileEvent.getFile();
        RootFolderEntity internalFolderEntity = this.rootFolderService.getByFolderId(fileEvent.getRootFolderId());
        FileEntity internalFileEntity = this.fileService.getFileEntityFromFile(
                internalFolderEntity.getRootFolderId(),
                internalFolderEntity.getRootFolderFullPath(),
                internalFile
        );
        // 根据 internalFolderId -> sync-flow -> contentFolderEntity
        List<SyncFlowEntity> syncFlowList =
                this.syncFlowService.getInternal2ContentSyncFlowListByFolderId(fileEvent.getRootFolderId());
        if (CollectionUtils.isEmpty(syncFlowList)) {
            throw new SyncDuoException(
                    "创建 content file 失败,没有找到对应的 sync flow. fileEvent 是 %s".formatted(fileEvent));
        }
        // 每一个 content folder 执行一样的操作
        for (SyncFlowEntity syncFlowEntity : syncFlowList) {
            // 获取 content folder entity
            RootFolderEntity contentFolderEntity =
                    this.rootFolderService.getByFolderId(syncFlowEntity.getDestFolderId());
            FileEntity contentFileEntity = this.fileService.getInternalFileEntityFromSourceEntity(
                    contentFolderEntity.getRootFolderId(),
                    internalFileEntity);
            Path contentFile = this.fileService.getFileFromFileEntity(
                    contentFolderEntity.getRootFolderFullPath(),
                    contentFileEntity);
            if (ObjectUtils.isNotEmpty(contentFile)) {
                // 找到文件则 update with copy, 找不到文件说明已经过滤了, 则不需要 update with copy
                // 找到了则 file update copy
                contentFile = fileAccessValidator.updateFileByCopy(
                        internalFolderEntity.getRootFolderId(),
                        internalFile,
                        contentFolderEntity.getRootFolderId(),
                        contentFile);
                this.fileService.updateFileEntityByFile(contentFileEntity, contentFile);
                // 记录 file event
                FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, contentFileEntity);
                this.fileEventService.save(fileEventEntity);
            }
            // 减少 pending event count
            this.syncFlowService.decrInternal2ContentEventCount(syncFlowEntity.getDestFolderId());
        }
    }

    private void onFileDeSynced(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(rootFolderId);
        FileEntity contentFileEntity =
                this.fileService.getFileEntityFromFile(rootFolderId, rootFolderEntity.getRootFolderFullPath(), file);
        // file desynced
        contentFileEntity.setFileDesync(FileDesyncEnum.FILE_DESYNC.getCode());
        this.fileService.updateFileEntityByFile(contentFileEntity, file);
        // 记录 file event
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, contentFileEntity);
        this.fileEventService.save(fileEventEntity);
    }

    private void onFileDeleteFromContentFolder(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(rootFolderId);
        FileEntity contentFileEntity = this.fileService.getFileEntityFromFile(
                rootFolderId,
                rootFolderEntity.getRootFolderFullPath(),
                file);
        contentFileEntity.setFileDesync(FileDesyncEnum.FILE_DESYNC.getCode());
        this.fileService.deleteBatchByFileEntity(Collections.singletonList(contentFileEntity));
        // 记录 file event
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, contentFileEntity);
        this.fileEventService.save(fileEventEntity);
    }

    private FileEventEntity fillFileEventEntityFromFileEvent(
            FileEventDto fileEvent, FileEntity fileEntity) throws SyncDuoException {
        if (ObjectUtils.anyNull(fileEvent, fileEntity)) {
            throw new SyncDuoException("fillFileEventEntityFromFileEvent failed." +
                    " fileEventDto or fileEntity is null");
        }
        FileEventEntity fileEventEntity = new FileEventEntity();
        fileEventEntity.setRootFolderId(fileEntity.getRootFolderId());
        fileEventEntity.setFileId(fileEntity.getFileId());
        fileEventEntity.setFileEventType(fileEvent.getFileEventTypeEnum().name());
        return fileEventEntity;
    }

    @Override
    public void destroy() {
        log.info("stop content handler");
        RUNNING = false;
    }
}
