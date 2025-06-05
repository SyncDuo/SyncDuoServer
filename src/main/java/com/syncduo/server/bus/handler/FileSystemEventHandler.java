package com.syncduo.server.bus.handler;

import com.syncduo.server.bus.FileOperationMonitor;
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

    private final FileOperationMonitor fileOperationMonitor;

    private volatile boolean RUNNING = true;  // Flag to control the event loop

    @Autowired
    public FileSystemEventHandler(SystemBus systemBus,
                                  FileService fileService,
                                  FolderService folderService,
                                  FileEventService fileEventService,
                                  FileSyncMappingService fileSyncMappingService,
                                  FileOperationMonitor fileOperationMonitor) {
        this.systemBus = systemBus;
        this.fileService = fileService;
        this.folderService = folderService;
        this.fileEventService = fileEventService;
        this.fileSyncMappingService = fileSyncMappingService;
        this.fileOperationMonitor = fileOperationMonitor;
    }

    @Async("longRunningTaskExecutor")
    public void startHandle() {
        while (RUNNING) {
            FileSystemEvent fileSystemEvent = systemBus.getFileEvent();
            if (ObjectUtils.isEmpty(fileSystemEvent) ||
                    fileOperationMonitor.isFileEventInValid(fileSystemEvent.getFolderId())) {
                continue;
            }
            log.debug("fileSystemEvent is: {}", fileSystemEvent);
            this.dispatchHandle(fileSystemEvent);
        }
    }

    @Async("threadPoolTaskExecutor")
    protected void dispatchHandle(FileSystemEvent fileSystemEvent) {
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
            // 文件重复不是一个错误, 因为在这个系统设计里面, 有负反馈机制
            // 1. 文件创建在 folder a, 通过 downStreamHandler, 文件会复制到 folder b 并创建 file entity
            // 2. 此时就会触发 watcher b, 从而报出 file entity 已存在
            log.debug("fileEntity already exist!. fileEvent is {}", fileSystemEvent);
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
        if (!this.fileService.isFileEntityDiffFromFile(fileEntity, fileSystemEvent.getFile())) {
            // file entity 已经修改不是一个错误, 因为在这个系统设计里面, 有负反馈机制
            // 1. 文件修改在 folder a, 通过 downStreamHandler, 文件的修改会传导到 folder b 并修改 file 和 file entity
            // 2. 此时就会触发 watcher b, 从而报出 file entity 已修改
            log.debug("fileEntity already change!. fileEvent is {}", fileSystemEvent);
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
            // 说明是通过 db 和 filesystem 主动比较出来的, 使用 fileEntityNotInFileSystem
            fileEntity = fileSystemEvent.getFileEntityNotInFilesystem();
        } else {
            fileEntity = this.fileService.getFileEntityFromFile(
                    folderEntity.getFolderId(),
                    folderEntity.getFolderFullPath(),
                    fileSystemEvent.getFile()
            );
            if (ObjectUtils.isEmpty(fileEntity)) {
                // 文件删除不存在负反馈, 因为文件删除在整个系统中是 ignore delete 的
                log.warn("onFileDelete failed. fileEntity is not exist. fileEvent is {}", fileSystemEvent);
                return;
            }
        }
        // 删除 file entity
        this.fileService.removeById(fileEntity);
        // 如果删除的文件是"下游", 则在 file_sync_mapping 中标记为 desync, 表示不需要从上游同步
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
