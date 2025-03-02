package com.syncduo.server.bus;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileEvent;
import com.syncduo.server.service.bussiness.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class FileEventHandler implements DisposableBean {

    private final SystemBus systemBus;

    private final FileService fileService;

    private final FolderService folderService;

    private final FileEventService fileEventService;

    private final FileSyncMappingService fileSyncMappingService;

    private final SyncFlowService syncFlowService;

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final FileAccessValidator fileAccessValidator;

    private volatile boolean RUNNING = true;  // Flag to control the event loop

    @Autowired
    public FileEventHandler(SystemBus systemBus,
                            FileService fileService,
                            FolderService folderService,
                            FileEventService fileEventService,
                            FileSyncMappingService fileSyncMappingService,
                            SyncFlowService syncFlowService,
                            ThreadPoolTaskExecutor threadPoolTaskExecutor,
                            FileAccessValidator fileAccessValidator) {
        this.systemBus = systemBus;
        this.fileService = fileService;
        this.folderService = folderService;
        this.fileEventService = fileEventService;
        this.fileSyncMappingService = fileSyncMappingService;
        this.syncFlowService = syncFlowService;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.fileAccessValidator = fileAccessValidator;
    }

    @Async("threadPoolTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            FileEvent fileEvent = systemBus.getFileEvent();
            if (ObjectUtils.isEmpty(fileEvent) ||
                    fileAccessValidator.isFileEventInValid(fileEvent.getFolderId())) {
                continue;
            }
            this.threadPoolTaskExecutor.submit(() -> {
                try {
                    switch (fileEvent.getFileEventTypeEnum()) {
                        case FILE_CREATED -> this.onFileCreate(fileEvent);
                        case FILE_CHANGED -> this.onFileChange(fileEvent);
                        case FILE_DELETED -> this.onFileDelete(fileEvent);
                        default -> throw new SyncDuoException("文件夹的文件事件:%s 不识别".formatted(fileEvent));
                    }
                } catch (SyncDuoException e) {
                    log.error("文件夹的文件事件:{} 处理失败", fileEvent, e);
                    this.syncFlowService.decrPendingEventCount(fileEvent.getFolderId());
                }
            });
        }
    }

    private void onFileCreate(FileEvent fileEvent) throws SyncDuoException {
        // 幂等
        FolderEntity folderEntity = this.folderService.getById(fileEvent.getFolderId());
        FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                folderEntity.getFolderId(),
                folderEntity.getFolderFullPath(),
                fileEvent.getFile()
        );
        if (ObjectUtils.isNotEmpty(fileEntity)) {
            throw new SyncDuoException("onFileCreate failed. fileEntity already exist!." +
                    "fileEvent is %s".formatted(fileEvent));
        }
        // 创建文件记录
        fileEntity = this.fileService.createFileRecord(
                folderEntity.getFolderId(),
                folderEntity.getFolderFullPath(),
                fileEvent.getFile()
        );
        durationAndDownStream(fileEvent, folderEntity, fileEntity);
    }

    private void onFileChange(FileEvent fileEvent) throws SyncDuoException {
        // 幂等
        FolderEntity folderEntity = this.folderService.getById(fileEvent.getFolderId());
        FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                folderEntity.getFolderId(),
                folderEntity.getFolderFullPath(),
                fileEvent.getFile()
        );
        if (ObjectUtils.isEmpty(fileEntity)) {
            throw new SyncDuoException("onFileChange failed. fileEntity is not exist." +
                    "fileEvent is %s".formatted(fileEvent));
        }
        // 更新 file entity
        this.fileService.updateFileEntityByFile(
                fileEntity,
                fileEvent.getFile()
        );
        durationAndDownStream(fileEvent, folderEntity, fileEntity);
    }

    private void onFileDelete(FileEvent fileEvent) throws SyncDuoException {
        // 幂等
        FolderEntity folderEntity = this.folderService.getById(fileEvent.getFolderId());
        FileEntity fileEntity;
        if (ObjectUtils.isEmpty(fileEvent.getFile())) {
            fileEntity = fileEvent.getFileEntityNotInFilesystem();
        } else {
            fileEntity = this.fileService.getFileEntityFromFile(
                    folderEntity.getFolderId(),
                    folderEntity.getFolderFullPath(),
                    fileEvent.getFile()
            );
            if (ObjectUtils.isEmpty(fileEntity)) {
                throw new SyncDuoException("onFileDelete failed. can't find fileEntity." +
                        "fileEvent is %s".formatted(fileEvent));
            }
        }
        // 删除 file entity
        this.fileService.deleteBatchByFileEntity(Collections.singletonList(fileEntity));
        // 判断是否 desynced
        this.fileSyncMappingService.desyncByFileId(fileEntity.getFileId());
        durationAndDownStream(fileEvent, folderEntity, fileEntity);
    }

    private void durationAndDownStream(
            FileEvent fileEvent,
            FolderEntity folderEntity,
            FileEntity fileEntity) throws SyncDuoException {
        // 创建 fileEvent
        this.fileEventService.createFileEvent(
                fileEvent.getFileEventTypeEnum().name(),
                folderEntity.getFolderId(),
                fileEntity.getFileId()
        );
        // 传递下游事件
        DownStreamEvent downStreamEvent = new DownStreamEvent(
                folderEntity,
                fileEntity,
                fileEvent.getFile(),
                fileEvent.getFileEventTypeEnum()
        );
        this.systemBus.sendDownStreamEvent(downStreamEvent);
    }

    @Override
    public void destroy() {
        log.info("stop file event handler");
        RUNNING = false;
    }
}
