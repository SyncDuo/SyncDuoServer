package com.syncduo.server.mq.consumer;

import com.syncduo.server.enums.FileDeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.dto.mq.FileMessageDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.EventQueue;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.util.UUID;

@Service
@Slf4j
public class SourceHandler {

    @Value("${syncduo.server.event.polling.num:10}")
    private Integer pollingNum;

    private final EventQueue eventQueue;

    private final FileService fileService;

    private final FileOperationService fileOperationService;

    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final FileEventService fileEventService;

    @Autowired
    public SourceHandler(EventQueue eventQueue,
                         FileService fileService,
                         FileOperationService fileOperationService,
                         RootFolderService rootFolderService,
                         SyncFlowService syncFlowService,
                         FileEventService fileEventService) {
        this.eventQueue = eventQueue;
        this.fileService = fileService;
        this.fileOperationService = fileOperationService;
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileEventService = fileEventService;
    }

    private void handle() {
        for (int i = 0; i < pollingNum; i++) {
            FileEventDto fileEvent = eventQueue.pollSourceFolderEvent();
            if (ObjectUtils.isEmpty(fileEvent)) {
                break;
            }

            try {
                switch (fileEvent.getFileEventType()) {
                    case SOURCE_FOLDER_FILE_CREATED -> this.onFileChange(fileEvent);
                    case SOURCE_FOLDER_FILE_CHANGED -> this.onFileCreate(fileEvent);
                    case SOURCE_FOLDER_FILE_DELETED -> this.onFileDelete(fileEvent);
                    default -> throw new SyncDuoException("源文件夹的文件事件:%s 不识别".formatted(fileEvent));
                }
            } catch (SyncDuoException e) {
                log.error("源文件夹的文件事件:%s 处理失败".formatted(fileEvent));
            }
        }
    }

    private void onFileCreate(FileEventDto fileEvent) {
        FileEntity fileEntity;
        try {
            // 根据 fileEvent 填充 fileEntity
            fileEntity = this.fillFileEntityForCreate(fileEvent);
            // 更新 file 表
            this.fileService.createFileRecord(fileEntity);
        } catch (SyncDuoException e) {
            log.warn("处理 source folder 文件新建失败", e);
            return;
        }

        // 发送 event 到 internal 队列
        this.eventQueue.pushInternalEvent(new FileMessageDto(fileEvent, fileEntity));
    }

    private void onFileChange(FileEventDto fileEvent) {
        Path file = fileEvent.getFile();

        FileEntity fileEntity;
        try {
            // 根据 fileEvent 查数据库获取 fileEntity
            fileEntity = this.getFileEntityFromFileEvent(fileEvent);
            // 更新  md5checksum,last_modified_time
            Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
            fileEntity.setLastUpdatedTime(fileCrTimeAndMTime.getRight());
            String md5Checksum = FileOperationUtils.getMD5Checksum(file);
            fileEntity.setFileMd5Checksum(md5Checksum);
        } catch (SyncDuoException e) {
            log.warn("处理 source folder 文件修改失败", e);
            return;
        }

        // 更新数据库
        this.fileService.updateById(fileEntity);
        // 发送 event 到 internal 队列
        this.eventQueue.pushInternalEvent(new FileMessageDto(fileEvent, fileEntity));
    }

    private void onFileDelete(FileEventDto fileEvent) throws SyncDuoException {
        FileEntity fileEntity;
        try {
            // 根据 fileEvent 查数据库获取 fileEntity
            fileEntity = this.getFileEntityFromFileEvent(fileEvent);
            // 更新 file_deleted=1
            fileEntity.setFileDeleted(FileDeletedEnum.FILE_DELETED.getCode());
        } catch (SyncDuoException e) {
            log.warn("处理 source folder 文件修改失败", e);
            return;
        }

        // 更新数据库
        this.fileService.updateById(fileEntity);
    }

    private FileEntity getFileEntityFromFileEvent(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolder = this.rootFolderService.getByFolderId(rootFolderId);
        String folderFullPath = rootFolder.getFolderFullPath();
        String relativePath = FileOperationUtils.getFileParentFolderRelativePath(folderFullPath, file);

        String uuid4 = FileOperationUtils.getUuid4(folderFullPath, relativePath, file.getFileName().toString());
        FileEntity dbResult = this.fileService.getByUuid4(uuid4);
        if (ObjectUtils.isEmpty(dbResult)) {
            throw new SyncDuoException("被修改的文件没有记录在数据库, fileEvent 是 %s".formatted(fileEvent));
        }
        return dbResult;
    }

    private FileEntity fillFileEntityForCreate(FileEventDto fileEvent) throws SyncDuoException {
        FileEntity fileEntity = new FileEntity();

        Path file = fileEvent.getFile();

        // 获取 created_time and last_modified_time
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        fileEntity.setFileCreatedTime(fileCrTimeAndMTime.getLeft());
        fileEntity.setFileLastModifiedTime(fileCrTimeAndMTime.getRight());

        // 获取 md5 checksum
        String md5Checksum = FileOperationUtils.getMD5Checksum(file);
        fileEntity.setFileMd5Checksum(md5Checksum);

        // 根据 root folder id 获取 root folder path, 从而计算 relative path
        Long rootFolderId = fileEvent.getRootFolderId();
        fileEntity.setRootFolderId(rootFolderId);
        RootFolderEntity rootFolder = this.rootFolderService.getByFolderId(rootFolderId);
        String folderFullPath = rootFolder.getFolderFullPath();
        String relativePath = FileOperationUtils.getFileParentFolderRelativePath(folderFullPath, file);
        fileEntity.setRelativePath(relativePath);

        // 获取 file name 和 file extension
        Pair<String, String> fileNameAndExtension = FileOperationUtils.getFileNameAndExtension(file);
        fileEntity.setFileName(fileNameAndExtension.getLeft());
        fileEntity.setFileExtension(fileNameAndExtension.getRight());

        // source folder 的文件表, derived_file_id 为 0
        fileEntity.setDerivedFileId(0L);

        // 获取 uuid4
        String uuid4 = FileOperationUtils.getUuid4(folderFullPath, relativePath, file.getFileName().toString());
        fileEntity.setFileUuid4(uuid4);

        return fileEntity;
    }
}
