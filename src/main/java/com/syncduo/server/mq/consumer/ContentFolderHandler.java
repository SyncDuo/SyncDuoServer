package com.syncduo.server.mq.consumer;

import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ContentFolderHandler {
    @Value("${syncduo.server.event.polling.num:10}")
    private Integer pollingNum;

    private final SystemQueue systemQueue;

    private final FileService fileService;

    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final FileEventService fileEventService;

    private final SyncSettingService syncSettingService;

    public ContentFolderHandler(
            SystemQueue systemQueue,
            FileService fileService,
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            FileEventService fileEventService,
            SyncSettingService syncSettingService) {
        this.systemQueue = systemQueue;
        this.fileService = fileService;
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileEventService = fileEventService;
        this.syncSettingService = syncSettingService;
    }

    // full scan content folder 触发. root folder type = content folder, 则不需要 file copy
    // internal folder 触发, root folder type = internal folder, 需要 file copy
    // internal 和 content folder compare 触发, root folder type = internal folder, 需要 file copy
    // todo: 并发执行
    @Scheduled(fixedDelayString = "${syncduo.server.event.polling.interval:5000}")
    private void handleFileEvent() {
        for (int i = 0; i < pollingNum; i++) {
            FileEventDto fileEvent = this.systemQueue.pollContentFolderEvent();
            if (ObjectUtils.isEmpty(fileEvent)) {
                break;
            }
            try {
                switch (fileEvent.getFileEventTypeEnum()) {
                    case FILE_CREATED -> {
                        switch (fileEvent.getRootFolderTypeEnum()) {
                            case INTERNAL_FOLDER -> this.onFileCreateFromInternalFolder(fileEvent);
                            case CONTENT_FOLDER -> this.onFileCreateFromContentFolder(fileEvent);
                    }}
                    case FILE_CHANGED -> {
                        switch (fileEvent.getRootFolderTypeEnum()) {
                            case INTERNAL_FOLDER -> this.onFileChangeFromInternalFolder(fileEvent);
                            case CONTENT_FOLDER -> this.onFileChangeFromContentFolder(fileEvent);
                        }
                    }
                    case FILE_DESYNCED -> this.onFileDeSynced(fileEvent);
                    case FILE_DELETED -> this.onFileDeleteFromContentFolder(fileEvent);
                    default -> throw new SyncDuoException("content 文件夹的文件事件:%s 不识别".formatted(fileEvent));
                }
            } catch (SyncDuoException e) {
                log.error("content 文件夹的文件事件:{} 处理失败", fileEvent, e);
            }
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
            this.fileService.createFileRecord(contentFileEntity);
        }
        // 记录 file event
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
                this.syncFlowService.getInternalSyncFlowByFolderId(fileEvent.getRootFolderId());
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
                this.syncFlowService.decrInternal2ContentCount(syncFlowEntity);
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
                this.syncFlowService.decrInternal2ContentCount(syncFlowEntity);
                continue;
            }
            // file copy
            Path contentFile =
                    FileOperationUtils.copyFile(internalFile.toAbsolutePath().toString(), contentFileFullPath);
            // create record
            FileEntity contentFileEntity = this.fileService.fillFileEntityForCreate(
                    contentFile,
                    contentFolderEntity.getRootFolderId(),
                    contentFolderEntity.getRootFolderFullPath()
            );
            this.fileService.createFileRecord(contentFileEntity);
            // 减少 pending event count
            this.syncFlowService.decrInternal2ContentCount(syncFlowEntity);
            // 记录 file event
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
                this.syncFlowService.getInternalSyncFlowByFolderId(fileEvent.getRootFolderId());
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
                contentFile = FileOperationUtils.updateFileByCopy(internalFile, contentFile);
                this.fileService.updateFileEntityByFile(contentFileEntity, contentFile);
                // 记录 file event
            }
            // 减少 pending event count
            this.syncFlowService.decrInternal2ContentCount(syncFlowEntity);
        }
    }

    private void onFileChangeFromContentFolder(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(rootFolderId);
        FileEntity contentFileEntity =
                this.fileService.getFileEntityFromFile(rootFolderId, rootFolderEntity.getRootFolderFullPath(), file);
        this.fileService.updateFileEntityByFile(contentFileEntity, file);
        // 记录 file event
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
    }

    private void onFileDeleteFromContentFolder(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(rootFolderId);
        FileEntity contentFileEntity =
                this.fileService.getFileEntityFromFile(rootFolderId, rootFolderEntity.getRootFolderFullPath(), file);
        contentFileEntity.setFileDesync(FileDesyncEnum.FILE_DESYNC.getCode());
        this.fileService.deleteBatchByFileEntity(Collections.singletonList(contentFileEntity));
        // 记录 file event
    }
}
