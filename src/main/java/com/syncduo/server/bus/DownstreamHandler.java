package com.syncduo.server.bus;

import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileEvent;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class DownstreamHandler implements DisposableBean {

    private final SystemBus systemBus;

    private final FileService fileService;

    private final FolderService folderService;

    private final FileEventService fileEventService;

    private final FileSyncMappingService fileSyncMappingService;

    private final SyncSettingService syncSettingService;

    private final SyncFlowService syncFlowService;

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final FileAccessValidator fileAccessValidator;

    private volatile boolean RUNNING = true;  // Flag to control the event loop

    @Autowired
    public DownstreamHandler(SystemBus systemBus,
                             FileService fileService,
                             FolderService folderService,
                             FileEventService fileEventService,
                             FileSyncMappingService fileSyncMappingService,
                             SyncSettingService syncSettingService,
                             SyncFlowService syncFlowService,
                             ThreadPoolTaskExecutor threadPoolTaskExecutor,
                             FileAccessValidator fileAccessValidator) {
        this.systemBus = systemBus;
        this.fileService = fileService;
        this.folderService = folderService;
        this.fileEventService = fileEventService;
        this.fileSyncMappingService = fileSyncMappingService;
        this.syncSettingService = syncSettingService;
        this.syncFlowService = syncFlowService;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.fileAccessValidator = fileAccessValidator;
    }

    @Async("threadPoolTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            DownStreamEvent downStreamEvent = systemBus.getDownStreamEvent();
            if (ObjectUtils.isEmpty(downStreamEvent)) {
                continue;
            }
            this.threadPoolTaskExecutor.submit(() -> {
                try {
                    switch (downStreamEvent.getFileEventTypeEnum()) {
                        case FILE_CREATED -> this.onFileCreate(downStreamEvent);
                        case FILE_CHANGED -> this.onFileChange(downStreamEvent);
                        case FILE_DELETED -> this.onFileDelete(downStreamEvent);
                        default -> throw new SyncDuoException("下游事件:%s 不识别".formatted(downStreamEvent));
                    }
                } catch (SyncDuoException e) {
                    log.error("startHandle failed. downStreamEvent:{} failed!", downStreamEvent, e);
                } finally {
                    try {
                        this.syncFlowService.decrPendingEventCount(downStreamEvent.getFolderEntity().getFolderId());
                    } catch (SyncDuoException e) {
                        log.error("startHandle failed. pending event count can't decrease", e);
                    }
                }
            });
        }
    }

    private void onFileCreate(DownStreamEvent downStreamEvent) throws SyncDuoException {
        FolderEntity folderEntity = downStreamEvent.getFolderEntity();
        FileEntity fileEntity = downStreamEvent.getFileEntity();
        Path file = downStreamEvent.getFile();
        // 查询 syncflow
        List<SyncFlowEntity> syncFlowEntityList =
                this.syncFlowService.getBySourceIdFromCache(folderEntity.getFolderId());
        if (CollectionUtils.isEmpty(syncFlowEntityList)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
            // 幂等
            FolderEntity destFolderEntity = this.folderService.getById(syncFlowEntity.getDestFolderId());
            if (ObjectUtils.isEmpty(destFolderEntity)) {
                log.error("folder entity is null. syncflow is {}", syncFlowEntity);
                continue;
            }
            // 执行文件创建
            String destFilePath = this.getDestFilePathOnFileCreate(
                    fileEntity,
                    destFolderEntity,
                    file,
                    syncFlowEntity
            );
            // 空白说明文件 filtered, 则不执行文件创建
            if (StringUtils.isBlank(destFilePath)) {
                continue;
            }
            Path destFile = this.fileAccessValidator.copyFile(
                    folderEntity.getFolderId(),
                    folderEntity.getFolderFullPath(),
                    destFolderEntity.getFolderId(),
                    destFilePath
            );
            // 创建文件记录
            FileEntity destFileEntity = this.fileService.createFileRecord(
                    destFolderEntity.getFolderId(),
                    destFolderEntity.getFolderFullPath(),
                    destFile
            );
            // 创建 fileSyncMapping 记录
            this.fileSyncMappingService.createRecord(
                    syncFlowEntity.getSyncFlowId(),
                    fileEntity.getFileId(),
                    destFileEntity.getFileId()
            );
            // 记录 fileEvent
            this.fileEventService.createFileEvent(
                    downStreamEvent.getFileEventTypeEnum(),
                    destFolderEntity.getFolderId(),
                    destFileEntity.getFileId()
            );
        }
    }

    private void onFileChange(DownStreamEvent downStreamEvent) throws SyncDuoException {
        FolderEntity folderEntity = downStreamEvent.getFolderEntity();
        FileEntity fileEntity = downStreamEvent.getFileEntity();
        Path file = downStreamEvent.getFile();
        // 查询 syncflow
        List<SyncFlowEntity> syncFlowEntityList =
                this.syncFlowService.getBySourceIdFromCache(folderEntity.getFolderId());
        if (CollectionUtils.isEmpty(syncFlowEntityList)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
            // 幂等
            FolderEntity destFolderEntity = this.folderService.getById(syncFlowEntity.getDestFolderId());
            if (ObjectUtils.isEmpty(destFolderEntity)) {
                log.error("folder entity is null. syncflow is {}", syncFlowEntity);
                continue;
            }
            // 获取 destFile
            Pair<FileEntity, Path> destFileEntityAndFile = this.getDestFileOnFileChange(
                    fileEntity,
                    destFolderEntity,
                    file,
                    syncFlowEntity
            );
            if (ObjectUtils.isEmpty(destFileEntityAndFile)) {
                continue;
            }
            // 更新文件
            Path destFile = this.fileAccessValidator.updateFileByCopy(
                    folderEntity.getFolderId(),
                    file,
                    destFolderEntity.getFolderId(),
                    destFileEntityAndFile.getRight()
            );
            // 更新文件记录
            this.fileService.updateFileEntityByFile(
                    destFileEntityAndFile.getLeft(),
                    destFile
            );
            // 记录 fileEvent
            this.fileEventService.createFileEvent(
                    downStreamEvent.getFileEventTypeEnum(),
                    destFolderEntity.getFolderId(),
                    destFileEntityAndFile.getLeft().getFileId()
            );
        }
    }

    private void onFileDelete(DownStreamEvent downStreamEvent) throws SyncDuoException {
        // 上游删除的文件, 下游标记为 desynced
        FileEntity fileEntity = downStreamEvent.getFileEntity();
        this.fileSyncMappingService.desyncBySourceFileId(fileEntity.getFileId());
    }

    private Pair<FileEntity, Path> getDestFileOnFileChange(
            FileEntity sourceFileEntity,
            FolderEntity destFolderEntity,
            Path file,
            SyncFlowEntity syncFlowEntity
    ) throws SyncDuoException {
        // 找不到 fileSyncMappingEntity, 则不需要更新
        FileSyncMappingEntity fileSyncMappingEntity = this.fileSyncMappingService.getBySyncFlowIdAndFileId(
                syncFlowEntity.getSyncFlowId(),
                sourceFileEntity.getFileId());
        if (ObjectUtils.isEmpty(fileSyncMappingEntity)) {
            return null;
        }
        // 已经 desync, 则不需要更新
        if (FileDesyncEnum.FILE_DESYNC.getCode() == fileSyncMappingEntity.getFileDesync()) {
            return null;
        }
        Long syncFlowId = syncFlowEntity.getSyncFlowId();
        if (this.syncSettingService.isFilter(syncFlowId, file)) {
            // 根据最新的 filter criteria 已经 filter 了, 则需要删除 fileSyncMapping
            this.fileSyncMappingService.deleteRecord(fileSyncMappingEntity);
            return null;
        }
        // 获取 destFileEntity, 为空则表示已删除
        FileEntity destFileEntity = this.fileService.getById(fileSyncMappingEntity.getDestFileId());
        if (ObjectUtils.isEmpty(destFileEntity)) {
            return null;
        }
        // 根据 destFileEntity, 获取 file
        Path destFile = this.fileService.getFileFromFileEntity(destFolderEntity.getFolderFullPath(), destFileEntity);
        return new ImmutablePair<>(destFileEntity, destFile);
    }

    private String getDestFilePathOnFileCreate(
            FileEntity sourceFileEntity,
            FolderEntity destFolderEntity,
            Path file,
            SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        Long syncFlowId = syncFlowEntity.getSyncFlowId();
        if (this.syncSettingService.isFilter(syncFlowId, file)) {
            return "";
        }
        // 使用 mirror 模式, destFileFullPath = destFolderFullPath + fileEntity.relativePath + fileName.fileExtension
        String  destFileFullPath = destFolderEntity.getFolderFullPath() +
                sourceFileEntity.getRelativePath() +
                file.getFileName();
        // 使用 flatten 模式, destFileFullPath = destFolderFullPath + / + fileNewName.fileExtension
        if (!this.syncSettingService.isMirrored(syncFlowId)) {
            destFileFullPath = destFolderEntity.getFolderFullPath() +
                    FilesystemUtil.getPathSeparator() +
                    FilesystemUtil.getNewFileName(destFileFullPath);
        }
        return destFileFullPath;
    }

    @Override
    public void destroy() {
        log.info("stop downstream handler");
        RUNNING = false;
    }
}
