package com.syncduo.server.mq.consumer;

import com.syncduo.server.enums.FileDeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.dto.mq.FileMsgDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Timestamp;

@Service
@Slf4j
public class SourceFolderEventHandler {

    @Value("${syncduo.server.event.polling.num:10}")
    private Integer pollingNum;

    private final SystemQueue systemQueue;

    private final FileService fileService;

    private final RootFolderService rootFolderService;

    private final FileEventService fileEventService;

    @Autowired
    public SourceFolderEventHandler(SystemQueue systemQueue,
                                    FileService fileService,
                                    RootFolderService rootFolderService,
                                    FileEventService fileEventService) {
        this.systemQueue = systemQueue;
        this.fileService = fileService;
        this.rootFolderService = rootFolderService;
        this.fileEventService = fileEventService;
    }

    @Scheduled(fixedDelayString = "${syncduo.server.message.polling.interval:5000}")
    private void handle() {
        for (int i = 0; i < pollingNum; i++) {
            FileEventDto fileEvent = systemQueue.pollSourceFolderEvent();
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

    private void onFileCreate(FileEventDto fileEvent) throws SyncDuoException {
        // 根据 fileEvent 填充 fileEntity
        FileEntity fileEntity = this.fillFileEntityForCreate(fileEvent);
        // 更新 file 表
        this.fileService.createFileRecord(fileEntity);
        // 记录 file event, parentFileEventId=0, 表示 source folder 发生的文件事件
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, fileEntity);
        this.fileEventService.save(fileEventEntity);
        // 发送 event 到 internal 队列
        this.systemQueue.pushInternalFileMsg(new FileMsgDto(fileEntity, fileEventEntity));
    }

    private void onFileChange(FileEventDto fileEvent) throws SyncDuoException {
        Path file = fileEvent.getFile();
        // 根据 fileEvent 查数据库获取 fileEntity
        FileEntity fileEntity = this.getFileEntityFromFileEvent(fileEvent);
        // 更新  md5checksum,last_modified_time
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        fileEntity.setLastUpdatedTime(fileCrTimeAndMTime.getRight());
        String md5Checksum = FileOperationUtils.getMD5Checksum(file);
        fileEntity.setFileMd5Checksum(md5Checksum);
        // 更新数据库
        this.fileService.updateById(fileEntity);
        // 记录 file event, parentFileEventId=0, 表示 source folder 发生的文件事件
        FileEventEntity fileEventEntity = this.fillFileEventEntityFromFileEvent(fileEvent, fileEntity);
        this.fileEventService.save(fileEventEntity);
        // 发送 event 到 internal 队列
        this.systemQueue.pushInternalFileMsg(new FileMsgDto(fileEntity, fileEventEntity));
    }

    private void onFileDelete(FileEventDto fileEvent) throws SyncDuoException {
        // 根据 fileEvent 查数据库获取 fileEntity
        FileEntity fileEntity = this.getFileEntityFromFileEvent(fileEvent);
        // 更新 file_deleted=1
        fileEntity.setFileDeleted(FileDeletedEnum.FILE_DELETED.getCode());
        // 更新数据库
        this.fileService.updateById(fileEntity);
    }

    private FileEventEntity fillFileEventEntityFromFileEvent(
            FileEventDto fileEvent, FileEntity fileEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileEvent)) {
            throw new SyncDuoException("创建文件事件记录失败, fileEventDto 为空");
        }
        FileEventEntity fileEventEntity = new FileEventEntity();
        fileEventEntity.setParentFileEventId(0L);
        fileEventEntity.setRootFolderId(fileEvent.getRootFolderId());
        fileEventEntity.setFileId(fileEntity.getFileId());
        fileEventEntity.setFileEventType(fileEvent.getFileEventType().name());
        return fileEventEntity;
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
        Path file = fileEvent.getFile();
        Long rootFolderId = fileEvent.getRootFolderId();
        RootFolderEntity rootFolder = this.rootFolderService.getByFolderId(rootFolderId);
        FileEntity fileEntity = this.fileService.fillFileEntityForCreate(
                file, rootFolderId, rootFolder.getFolderFullPath());
        // source folder 的文件表, derived_file_id 为 0
        fileEntity.setDerivedFileId(0L);
        return fileEntity;
    }
}
