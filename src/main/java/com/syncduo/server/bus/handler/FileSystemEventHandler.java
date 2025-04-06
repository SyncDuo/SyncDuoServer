package com.syncduo.server.bus.handler;

import com.syncduo.server.bus.FileAccessValidator;
import com.syncduo.server.bus.SystemBus;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileSystemEvent;
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
public class FileSystemEventHandler implements DisposableBean {

    private final SystemBus systemBus;

    private final FileService fileService;

    private final FolderService folderService;

    private final FileEventService fileEventService;

    private final FileSyncMappingService fileSyncMappingService;

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final FileAccessValidator fileAccessValidator;

    private volatile boolean RUNNING = true;  // Flag to control the event loop

    @Autowired
    public FileSystemEventHandler(SystemBus systemBus,
                                  FileService fileService,
                                  FolderService folderService,
                                  FileEventService fileEventService,
                                  FileSyncMappingService fileSyncMappingService,
                                  ThreadPoolTaskExecutor threadPoolTaskExecutor,
                                  FileAccessValidator fileAccessValidator) {
        this.systemBus = systemBus;
        this.fileService = fileService;
        this.folderService = folderService;
        this.fileEventService = fileEventService;
        this.fileSyncMappingService = fileSyncMappingService;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.fileAccessValidator = fileAccessValidator;
    }

    @Async("threadPoolTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            FileSystemEvent fileSystemEvent = systemBus.getFileEvent();
            if (ObjectUtils.isEmpty(fileSystemEvent) ||
                    fileAccessValidator.isFileEventInValid(fileSystemEvent.getFolderId())) {
                continue;
            }
            this.threadPoolTaskExecutor.submit(() -> {
                try {
                    switch (fileSystemEvent.getFileEventTypeEnum()) {
                        case FILE_CREATED -> this.onFileCreate(fileSystemEvent);
                        case FILE_CHANGED -> this.onFileChange(fileSystemEvent);
                        case FILE_DELETED -> this.onFileDelete(fileSystemEvent);
                        default -> throw new SyncDuoException("文件夹的文件事件:%s 不识别".formatted(fileSystemEvent));
                    }
                } catch (SyncDuoException e) {
                    log.error("startHandle failed. fileEvent:{} failed!", fileSystemEvent, e);
                }
            });
        }
    }

    private void onFileCreate(FileSystemEvent fileSystemEvent) throws SyncDuoException {
        // 幂等
        FolderEntity folderEntity = this.folderService.getById(fileSystemEvent.getFolderId());
        FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                folderEntity.getFolderId(),
                folderEntity.getFolderFullPath(),
                fileSystemEvent.getFile()
        );
        if (ObjectUtils.isNotEmpty(fileEntity)) {
            log.error("onFileCreate failed. fileEntity already exist!. fileEvent is {}", fileSystemEvent);
            return;
        }
        // 创建文件记录
        fileEntity = this.fileService.createFileRecord(
                folderEntity.getFolderId(),
                folderEntity.getFolderFullPath(),
                fileSystemEvent.getFile()
        );
        durationAndDownStream(fileSystemEvent, folderEntity, fileEntity);
    }

    private void onFileChange(FileSystemEvent fileSystemEvent) throws SyncDuoException {
        // 幂等
        FolderEntity folderEntity = this.folderService.getById(fileSystemEvent.getFolderId());
        FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                folderEntity.getFolderId(),
                folderEntity.getFolderFullPath(),
                fileSystemEvent.getFile()
        );
        if (ObjectUtils.isEmpty(fileEntity)) {
            log.warn("onFileChange failed. fileEntity is not exist. fileEvent is {}", fileSystemEvent);
            return;
        }
        // 更新 file entity
        this.fileService.updateFileEntityByFile(
                fileEntity,
                fileSystemEvent.getFile()
        );
        durationAndDownStream(fileSystemEvent, folderEntity, fileEntity);
    }

    private void onFileDelete(FileSystemEvent fileSystemEvent) throws SyncDuoException {
        // 幂等
        FolderEntity folderEntity = this.folderService.getById(fileSystemEvent.getFolderId());
        FileEntity fileEntity;
        if (ObjectUtils.isEmpty(fileSystemEvent.getFile())) {
            fileEntity = fileSystemEvent.getFileEntityNotInFilesystem();
        } else {
            fileEntity = this.fileService.getFileEntityFromFile(
                    folderEntity.getFolderId(),
                    folderEntity.getFolderFullPath(),
                    fileSystemEvent.getFile()
            );
            if (ObjectUtils.isEmpty(fileEntity)) {
                log.warn("onFileDelete failed. fileEntity is not exist. fileEvent is {}", fileSystemEvent);
                return;
            }
        }
        // 删除 file entity
        this.fileService.deleteBatchByFileEntity(Collections.singletonList(fileEntity));
        // 判断是否 desynced, 如果删除的文件在 file_sync_mapping 中, 则标记为 desynced
        this.fileSyncMappingService.desyncByDestFileId(fileEntity.getFileId());
        durationAndDownStream(fileSystemEvent, folderEntity, fileEntity);
    }

    private void durationAndDownStream(
            FileSystemEvent fileSystemEvent,
            FolderEntity folderEntity,
            FileEntity fileEntity) throws SyncDuoException {
        // 创建 fileEvent
        this.fileEventService.createFileEvent(
                fileSystemEvent.getFileEventTypeEnum(),
                folderEntity.getFolderId(),
                fileEntity.getFileId()
        );
        // 传递下游事件
        DownStreamEvent downStreamEvent = DownStreamEvent.builder()
                .folderEntity(folderEntity)
                .fileEntity(fileEntity)
                .file(fileSystemEvent.getFile())
                .fileEventTypeEnum(fileSystemEvent.getFileEventTypeEnum())
                .build();
        this.systemBus.sendDownStreamEvent(downStreamEvent);
    }

    @Override
    public void destroy() {
        log.info("stop file event handler");
        RUNNING = false;
    }
}
