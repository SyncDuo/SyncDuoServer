package com.syncduo.server.service.facade;

import com.syncduo.server.bus.SystemBus;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.model.internal.FileEvent;
import com.syncduo.server.model.internal.JoinResult;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JoinUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;


@Service
@Slf4j
public class SystemManagementService {

    private FileService fileService;

    private FolderService folderService;

    private SystemBus systemBus;

    @Autowired
    public SystemManagementService(
            FileService fileService,
            FolderService folderService,
            SystemBus systemBus) {
        this.fileService = fileService;
        this.folderService = folderService;
        this.systemBus = systemBus;
    }

    // initial delay 5 minutes, fixDelay 30 minutes. unit is millisecond
    @Scheduled(initialDelay = 1000 * 60 * 5, fixedDelayString = "${syncduo.server.check.folder.insync.interval:1800000}")
    public void periodicalScan() throws SyncDuoException {
        List<FolderEntity> allFolder = this.folderService.getAllFolder();
        for (FolderEntity folderEntity : allFolder) {
            this.updateFolderFromFileSystem(folderEntity.getFolderId());
        }
    }

    public void updateFilterCriteria(
            SyncFlowEntity syncFlowEntity,
            SyncSettingEntity syncSettingEntity) throws SyncDuoException {
        // 取 source folder 全部的 fileEntity

        // 根据 syncSetting, 获取 filter 之后的 fileEntity

        // 计算 join, 获取 refilter 之后, 在 destFolder 应该删除的 fileEntity

        // 封装 downStreamEvent, type 为 refilter
    }

    // 需要发送消息, 消息处理的时候做好幂等即可
    public void updateFolderFromFileSystem(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("updateFolderFromFileSystem failed. folderId is null");
        }
        FolderEntity folderEntity = this.folderService.getById(folderId);
        // 获取 filesystem 的全部 file, 然后转换为 fileEntity
        List<Path> fileListInFilesystem = FilesystemUtil.getAllFileInFolder(folderEntity.getFolderFullPath());
        List<FileEntity> fileEntityListInDB = this.fileService.getAllFileByFolderId(folderId);
        // 计算 join
        JoinResult<FileEntity, Path> joinResult = JoinUtil.allJoin(
                fileEntityListInDB,
                fileListInFilesystem,
                FileEntity::getFileUniqueHash,
                v -> {
                    try {
                        return FilesystemUtil.getUniqueHash(
                                folderEntity.getFolderId(),
                                folderEntity.getFolderFullPath(),
                                v
                        );
                    } catch (SyncDuoException e) {
                        log.error("compute unique hash failed", e);
                        return "";
                    }
                }
        );
        // left outer 代表记录已不在 filesystem, 需要删除
        for (FileEntity fileEntity : joinResult.getLeftOuterResult()) {
            FileEvent fileEvent = new FileEvent(fileEntity.getFolderId(), fileEntity, FileEventTypeEnum.FILE_DELETED);
            this.systemBus.sendFileEvent(fileEvent);
        }
        // inner join 则需要进一步比较,判断文件是否修改
        for (ImmutablePair<FileEntity, Path> pair : joinResult.getInnerResult()) {
            FileEntity fileEntityInDB = pair.getLeft();
            Path fileInFilesystem = pair.getRight();
            Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FilesystemUtil.getFileCrTimeAndMTime(fileInFilesystem);
            String md5Checksum = FilesystemUtil.getMD5Checksum(fileInFilesystem);
            boolean change = false;
            if (!fileEntityInDB.getFileMd5Checksum().equals(md5Checksum)) {
                change = true;
            }
            if (!fileEntityInDB.getFileLastModifiedTime().equals(fileCrTimeAndMTime.getRight())) {
                change = true;
            }
            if (change) {
                FileEvent fileEvent = new FileEvent(
                        fileEntityInDB.getFolderId(),
                        fileInFilesystem,
                        FileEventTypeEnum.FILE_CHANGED
                );
                this.systemBus.sendFileEvent(fileEvent);
            }
        }
        // right outer 代表记录不在 DB, 需要新建
        for (Path fileInFilesystem : joinResult.getRightOuterResult()) {
            FileEvent fileEvent = new FileEvent(
                    folderId,
                    fileInFilesystem,
                    FileEventTypeEnum.FILE_CREATED
            );
            this.systemBus.sendFileEvent(fileEvent);
        }
    }
}
