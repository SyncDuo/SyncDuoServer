package com.syncduo.server.service.facade;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.bus.SystemBus;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileSystemEvent;
import com.syncduo.server.model.internal.JoinResult;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JoinUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
public class SystemManagementService {

    private final FileService fileService;

    private final FolderService folderService;

    private final SystemBus systemBus;

    private final FolderWatcher folderWatcher;

    private final SyncSettingService syncSettingService;

    private final FileSyncMappingService fileSyncMappingService;

    @Autowired
    public SystemManagementService(
            FileService fileService,
            FolderService folderService,
            SystemBus systemBus,
            FolderWatcher folderWatcher,
            SyncSettingService syncSettingService,
            FileSyncMappingService fileSyncMappingService) {
        this.fileService = fileService;
        this.folderService = folderService;
        this.systemBus = systemBus;
        this.folderWatcher = folderWatcher;
        this.syncSettingService = syncSettingService;
        this.fileSyncMappingService = fileSyncMappingService;
    }

    // initial delay 5 minutes, fixDelay 30 minutes. unit is millisecond
    @Scheduled(initialDelay = 1000 * 60 * 5, fixedDelayString = "${syncduo.server.check.folder.insync.interval:1800000}")
    public void periodicalScan() throws SyncDuoException {
        List<FolderEntity> allFolder = this.folderService.getAllFolder();
        for (FolderEntity folderEntity : allFolder) {
            this.updateFolderFromFileSystem(folderEntity.getFolderId());
        }
    }

    public void systemStartUp() throws SyncDuoException {
        List<FolderEntity> allFolder = this.folderService.getAllFolder();
        if (ObjectUtils.isEmpty(allFolder)) {
            return;
        }
        this.periodicalScan();
        for (FolderEntity folderEntity : allFolder) {
            this.folderWatcher.addWatcher(folderEntity);
        }
    }

    public void pauseAllSyncFlow() throws SyncDuoException {

    }

    // 用于处理 source folder 已存在, dest folder 新建的情况
    // 1. 扫描 source folder 全部 file, 发送 downStreamEvent
    public void sendDownStreamEventFromSourceFolder(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 获取 sourceFolder
        FolderEntity sourceFolderEntity = this.folderService.getById(syncFlowEntity.getSourceFolderId());
        // 从 DB 中获取 source folder 的全部 file
        List<FileEntity> allFileInSourceFolder =
                this.fileService.getAllFileByFolderId(sourceFolderEntity.getFolderId());
        for (FileEntity fileEntity : allFileInSourceFolder) {
            DownStreamEvent downStreamEvent = DownStreamEvent.builder()
                    .folderEntity(sourceFolderEntity)
                    .fileEntity(fileEntity)
                    .file(this.fileService.getFileFromFileEntity(sourceFolderEntity.getFolderFullPath(), fileEntity))
                    .fileEventTypeEnum(FileEventTypeEnum.DB_FILE_RETRIEVE)
                    .syncFlowEntity(syncFlowEntity)
                    .build();
            this.systemBus.sendDownStreamEvent(downStreamEvent);
        }
    }

    // destFolder 中的文件, 因为 filterCriteria 更新, 可能有四种变化
    // 1. 没有增加或减少
    // 2. 增加
    // 3. 减少
    // 4/ 增加和减少均有可能
    public void updateFilterCriteria(
            SyncFlowEntity syncFlowEntity,
            SyncSettingEntity syncSettingEntity) throws SyncDuoException {
        // 获取 sourceFolder
        FolderEntity sourceFolderEntity = this.folderService.getById(syncFlowEntity.getSourceFolderId());
        // 取 source folder 全部的 fileEntity
        List<FileEntity> allFileEntityInSourceFolder =
                this.fileService.getAllFileByFolderId(syncFlowEntity.getSourceFolderId());
        if (CollectionUtils.isEmpty(allFileEntityInSourceFolder)) {
            return;
        }
        // 根据 syncSetting, 获取 refilter 之后的 fileEntity
        List<FileEntity> refilterFileEntity = new ArrayList<>(allFileEntityInSourceFolder.size());
        for (FileEntity fileEntity : allFileEntityInSourceFolder) {
            if (!this.syncSettingService.isFilter(syncSettingEntity, fileEntity.getFileExtension())) {
                refilterFileEntity.add(fileEntity);
            }
        }
        // 获取全部 fileSyncSettingEntity
        List<FileSyncMappingEntity> allFileSyncMappingEntity =
                this.fileSyncMappingService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        // allFileSyncMappingEntity 和 refilter fileEntity 做 join
        JoinResult<FileSyncMappingEntity, FileEntity> joinResult1 = JoinUtil.allJoin(
                allFileSyncMappingEntity,
                refilterFileEntity,
                FileSyncMappingEntity::getSourceFileId,
                FileEntity::getFileId
        );
        // 左外集表示需要删除的, 因为不在 refilter 的集合中, 说明被过滤
        List<FileSyncMappingEntity> deleteFileSyncMappingEntity = joinResult1.getLeftOuterResult();
        // 左外集和 allSourceFileEntity 做 join, 取 innerResult, 表示需要删除的 sourceFileEntity
        JoinResult<FileSyncMappingEntity, FileEntity> joinResult2 = JoinUtil.allJoin(
                deleteFileSyncMappingEntity,
                allFileEntityInSourceFolder,
                FileSyncMappingEntity::getSourceFileId,
                FileEntity::getFileId
        );
        for (ImmutablePair<FileSyncMappingEntity, FileEntity> joinPair : joinResult2.getInnerResult()) {
            FileEntity fileEntity = joinPair.getRight();
            Path file = this.fileService.getFileFromFileEntity(
                    sourceFolderEntity.getFolderFullPath(),
                    fileEntity
            );
            this.systemBus.sendDownStreamEvent(
                    DownStreamEvent.builder()
                            .folderEntity(sourceFolderEntity)
                            .fileEntity(fileEntity)
                            .file(file)
                            .syncFlowEntity(syncFlowEntity)
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_REFILTER_DELETED)
                            .build()
            );
        }
        // 右外集表示需要新增的, 因为不在 fileSyncMappingEntity 中, 说明之前被过滤, refilter 之后放出
        List<FileEntity> rightOuterResult = joinResult1.getRightOuterResult();
        for (FileEntity fileEntity : rightOuterResult) {
            Path file = this.fileService.getFileFromFileEntity(
                    sourceFolderEntity.getFolderFullPath(),
                    fileEntity
            );
            this.systemBus.sendDownStreamEvent(
                    DownStreamEvent.builder()
                            .folderEntity(sourceFolderEntity)
                            .fileEntity(fileEntity)
                            .file(file)
                            .syncFlowEntity(syncFlowEntity)
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_REFILTER_CREATED)
                            .build()
            );
        }
    }

    // 需要发送消息, 消息处理的时候做好幂等即可
    public boolean updateFolderFromFileSystem(Long folderId) throws SyncDuoException {
        boolean result = false;
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
            FileSystemEvent fileSystemEvent = new FileSystemEvent(
                    fileEntity.getFolderId(),
                    fileEntity,
                    FileEventTypeEnum.FILE_DELETED
            );
            this.systemBus.sendFileEvent(fileSystemEvent);
            result = true;
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
                FileSystemEvent fileSystemEvent = new FileSystemEvent(
                        fileEntityInDB.getFolderId(),
                        fileInFilesystem,
                        FileEventTypeEnum.FILE_CHANGED
                );
                result = true;
                this.systemBus.sendFileEvent(fileSystemEvent);
            }
        }
        // right outer 代表记录不在 DB, 需要新建
        for (Path fileInFilesystem : joinResult.getRightOuterResult()) {
            FileSystemEvent fileSystemEvent = new FileSystemEvent(
                    folderId,
                    fileInFilesystem,
                    FileEventTypeEnum.FILE_CREATED
            );
            this.systemBus.sendFileEvent(fileSystemEvent);
            result = true;
        }
        return result;
    }
}
