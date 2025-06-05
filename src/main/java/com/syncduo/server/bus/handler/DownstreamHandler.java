package com.syncduo.server.bus.handler;

import com.syncduo.server.bus.FileOperationMonitor;
import com.syncduo.server.bus.SystemBus;
import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.service.cache.SyncFlowServiceCache;
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
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collections;
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

    private final SyncFlowServiceCache syncFlowServiceCache;

    private final FileOperationMonitor fileOperationMonitor;

    private volatile boolean RUNNING = true;  // Flag to control the event loop

    @Autowired
    public DownstreamHandler(SystemBus systemBus,
                             FileService fileService,
                             FolderService folderService,
                             FileEventService fileEventService,
                             FileSyncMappingService fileSyncMappingService,
                             SyncSettingService syncSettingService,
                             SyncFlowServiceCache syncFlowServiceCache,
                             FileOperationMonitor fileOperationMonitor) {
        this.systemBus = systemBus;
        this.fileService = fileService;
        this.folderService = folderService;
        this.fileEventService = fileEventService;
        this.fileSyncMappingService = fileSyncMappingService;
        this.syncSettingService = syncSettingService;
        this.syncFlowServiceCache = syncFlowServiceCache;
        this.fileOperationMonitor = fileOperationMonitor;
    }

    @Async("longRunningTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            DownStreamEvent downStreamEvent;
            try {
                downStreamEvent = systemBus.getDownStreamEvent();
            } catch (SyncDuoException e) {
                log.error("downStreamHandler get event failed", e);
                continue;
            }
            if (ObjectUtils.isEmpty(downStreamEvent)) {
                continue;
            }
            log.debug("downstream event is {}", downStreamEvent);
            this.dispatchHandle(downStreamEvent);
        }
    }

    @Async("threadPoolTaskExecutor")
    protected void dispatchHandle(DownStreamEvent downStreamEvent) {
        try {
            // 处理 downStreamEvent
            switch (downStreamEvent.getFileEventTypeEnum()) {
                case FILE_CREATED,
                     DB_FILE_RETRIEVE,
                     FILE_RESUME_CREATED-> this.onFileCreate(downStreamEvent);
                case FILE_CHANGED,
                     FILE_RESUME_CHANGED -> this.onFileChange(downStreamEvent);
                case FILE_DELETED,
                     FILE_RESUME_DELETED-> this.onFileDelete(downStreamEvent);
                default -> throw new SyncDuoException("下游事件:%s 不识别".formatted(downStreamEvent));
            }
        } catch (SyncDuoException e) {
            log.error("dispatchHandle failed. downStreamEvent:{} failed!", downStreamEvent, e);
        }
    }

    private void onFileCreate(DownStreamEvent downStreamEvent) throws SyncDuoException {
        FolderEntity folderEntity = downStreamEvent.getFolderEntity();
        FileEntity fileEntity = downStreamEvent.getFileEntity();
        Path file = downStreamEvent.getFile();
        SyncFlowEntity syncFlowEntity = downStreamEvent.getSyncFlowEntity();
        // 幂等
        FileSyncMappingEntity fileSyncMappingEntity = this.fileSyncMappingService.getBySyncFlowIdAndSourceFileId(
                syncFlowEntity.getSyncFlowId(),
                fileEntity.getFileId()
        );
        // file sync mapping 已存在, 则删除记录并重新创建
        if (ObjectUtils.isNotEmpty(fileSyncMappingEntity)) {
            this.fileSyncMappingService.deleteRecord(fileSyncMappingEntity);
        }
        // 创建文件
        downStreamFileCreate(downStreamEvent, syncFlowEntity, fileEntity, file, folderEntity);
    }

    private void onFileChange(DownStreamEvent downStreamEvent) throws SyncDuoException {
        FolderEntity folderEntity = downStreamEvent.getFolderEntity();
        FileEntity fileEntity = downStreamEvent.getFileEntity();
        Path file = downStreamEvent.getFile();
        SyncFlowEntity syncFlowEntity = downStreamEvent.getSyncFlowEntity();
        // 幂等
        FolderEntity destFolderEntity = this.folderService.getById(syncFlowEntity.getDestFolderId());
        if (ObjectUtils.isEmpty(destFolderEntity)) {
            return;
        }
        // 获取 destFile
        Pair<FileEntity, Path> destFileEntityAndFile = this.getDestFileOnFileChange(
                fileEntity,
                destFolderEntity,
                file,
                syncFlowEntity
        );
        if (ObjectUtils.isEmpty(destFileEntityAndFile)) {
            return;
        }
        // 更新文件
        Path destFile = this.fileOperationMonitor.updateFileByCopy(
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

    private void onFileDelete(
            DownStreamEvent downStreamEvent) throws SyncDuoException {
        SyncFlowEntity syncFlowEntity = downStreamEvent.getSyncFlowEntity();
        // 上游删除的文件, 下游标记为 desynced
        FileEntity fileEntity = downStreamEvent.getFileEntity();
        this.fileSyncMappingService.desyncBySyncFlowIdAndFileId(
                syncFlowEntity.getSyncFlowId(),
                fileEntity.getFileId()
        );
    }

    private void downStreamFileCreate(
            DownStreamEvent downStreamEvent,
            SyncFlowEntity syncFlowEntity,
            FileEntity fileEntity,
            Path file,
            FolderEntity folderEntity) throws SyncDuoException {
        // 幂等
        FolderEntity destFolderEntity = this.folderService.getById(syncFlowEntity.getDestFolderId());
        if (ObjectUtils.isEmpty(destFolderEntity)) {
            return;
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
            return;
        }
        Path destFile = this.fileOperationMonitor.copyFile(
                folderEntity.getFolderId(),
                file,
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

    private Pair<FileEntity, Path> getDestFileOnFileChange(
            FileEntity sourceFileEntity,
            FolderEntity destFolderEntity,
            Path file,
            SyncFlowEntity syncFlowEntity
    ) throws SyncDuoException {
        // 找不到 fileSyncMappingEntity, 则不需要更新
        FileSyncMappingEntity fileSyncMappingEntity = this.fileSyncMappingService.getBySyncFlowIdAndSourceFileId(
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
        if (ObjectUtils.isEmpty(destFile)) {
            return null;
        }
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
                FilesystemUtil.getPathSeparator() +
                file.getFileName();
        // 使用 flatten 模式, destFileFullPath = destFolderFullPath + / + fileNewName.fileExtension
        if (!this.syncSettingService.isMirrored(syncFlowId)) {
            String newFileName = FilesystemUtil.getNewFileName(
                    file.toAbsolutePath().toString(), destFolderEntity.getFolderFullPath()
            );
            destFileFullPath = destFolderEntity.getFolderFullPath() +
                    FilesystemUtil.getPathSeparator() +
                    newFileName;
        }
        return destFileFullPath;
    }

    @Override
    public void destroy() {
        log.info("stop downstream handler");
        RUNNING = false;
    }
}
