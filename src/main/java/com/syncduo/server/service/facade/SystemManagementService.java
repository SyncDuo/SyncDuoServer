package com.syncduo.server.service.facade;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.bus.SystemBus;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileSystemEvent;
import com.syncduo.server.model.internal.JoinResult;
import com.syncduo.server.model.internal.MatchResult;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JoinUtil;
import com.syncduo.server.util.MatchUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


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

    // initial delay 5 minutes, fixDelay 30 minutes. unit is millisecond
    @Scheduled(initialDelay = 1000 * 60 * 5, fixedDelayString = "${syncduo.server.check.folder.insync.interval:1800000}")
    public void periodicalScan() throws SyncDuoException {
        List<FolderEntity> allFolder = this.folderService.getAllFolder();
        for (FolderEntity folderEntity : allFolder) {
            this.updateFolderFromFileSystem(folderEntity.getFolderId());
        }
    }

    // 用于处理 source folder 已存在, dest folder 新建的情况
    // 1. 扫描 source folder 全部 file, 发送 downStreamEvent, EventType 为 DB_FILE_RETRIEVE
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

    // 基本假设: syncflow 停止, source folder 和 dest folder 的 db state 和 filesystem state 是一致的
    // 仅仅是 folder a 和 folder b 不是同步
    public boolean resumeSyncFlow(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // resume 的本质就是获得 source folder 新创建的/新修改的/新删除的文件,
        // 而 dest folder 没有的文件, 并且是 apply filter 的情况下
        // 所以 resume = refilter + changedMissTrack
        boolean result = false;
        // 获取 refilter 的 结果
        Pair<FolderEntity, List<FileEntity>> refilterResult = this.getRefilterFileEntityList(syncFlowEntity);
        if (ObjectUtils.isEmpty(refilterResult)) {
            return result;
        }
        FolderEntity sourceFolderEntity = refilterResult.getLeft();
        List<FileEntity> refilterFileEntityList = refilterResult.getRight();
        // 获取全部 fileSyncMappingEntity, 表示已经通过 syncFlow 同步到 dest folder 的 sourceFile
        List<FileSyncMappingEntity> allFileSyncMappingEntity =
                this.fileSyncMappingService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        // 获取因为 resume 之后 created 的文件
        List<FileEntity> createdMissingTrackFileEntityList = this.compareForFileCreatedMissingTrack(
                refilterFileEntityList,
                allFileSyncMappingEntity
        );
        for (FileEntity createdMissingTrackFileEntity : createdMissingTrackFileEntityList) {
            Path sourceFile = this.fileService.getFileFromFileEntity(
                    sourceFolderEntity.getFolderFullPath(),
                    createdMissingTrackFileEntity);
            DownStreamEvent downStreamEvent = DownStreamEvent.builder()
                    .folderEntity(sourceFolderEntity)
                    .fileEntity(createdMissingTrackFileEntity)
                    .file(sourceFile)
                    .fileEventTypeEnum(FileEventTypeEnum.FILE_RESUME_CREATED)
                    .syncFlowEntity(syncFlowEntity)
                    .build();
            this.systemBus.sendDownStreamEvent(downStreamEvent);
            result = true;
        }
        // 获取 resume 之后 changed 的文件
        List<FileEntity> changedMissingTrackFileEntityList = this.compareForFileChangedMissingTrack(
                refilterFileEntityList,
                allFileSyncMappingEntity
        );
        for (FileEntity changedMissingTrackFileEntity : changedMissingTrackFileEntityList) {
            Path sourceFile = this.fileService.getFileFromFileEntity(
                    sourceFolderEntity.getFolderFullPath(),
                    changedMissingTrackFileEntity
            );
            DownStreamEvent downStreamEvent = DownStreamEvent.builder()
                    .folderEntity(sourceFolderEntity)
                    .fileEntity(changedMissingTrackFileEntity)
                    .file(sourceFile)
                    .fileEventTypeEnum(FileEventTypeEnum.FILE_RESUME_CHANGED)
                    .syncFlowEntity(syncFlowEntity)
                    .build();
            this.systemBus.sendDownStreamEvent(downStreamEvent);
            result = true;
        }
        // 获取因为 resume 之后 deleted 的文件
        List<FileEntity> deletedMissingTrackFileEntityList = this.compareForFileDeletedMissingTrack(
                refilterFileEntityList,
                allFileSyncMappingEntity
        );
        for (FileEntity deletedMissingTrackFileEntity : deletedMissingTrackFileEntityList) {
            DownStreamEvent downStreamEvent = DownStreamEvent.builder()
                    .folderEntity(sourceFolderEntity)
                    .fileEntity(deletedMissingTrackFileEntity)
                    .fileEventTypeEnum(FileEventTypeEnum.FILE_RESUME_DELETED)
                    .syncFlowEntity(syncFlowEntity)
                    .build();
            this.systemBus.sendDownStreamEvent(downStreamEvent);
            result = true;
        }
        return result;
    }

    private Pair<FolderEntity, List<FileEntity>> getRefilterFileEntityList(
            SyncFlowEntity syncFlowEntity
    ) throws SyncDuoException {
        // 获取 sourceFolder
        FolderEntity sourceFolderEntity = this.folderService.getById(syncFlowEntity.getSourceFolderId());
        // 取 source folder 全部的 fileEntity
        List<FileEntity> allFileEntityInSourceFolder =
                this.fileService.getAllFileByFolderId(syncFlowEntity.getSourceFolderId());
        if (CollectionUtils.isEmpty(allFileEntityInSourceFolder)) {
            return null;
        }
        // 获取 sync setting
        SyncSettingEntity syncSettingEntity =
                this.syncSettingService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            return null;
        }
        // 根据 syncSetting, 获取 refilter 之后的 fileEntity
        List<FileEntity> refilterFileEntity = new ArrayList<>(allFileEntityInSourceFolder.size());
        for (FileEntity fileEntity : allFileEntityInSourceFolder) {
            if (!this.syncSettingService.isFilter(syncSettingEntity, fileEntity.getFileExtension())) {
                refilterFileEntity.add(fileEntity);
            }
        }
        return new ImmutablePair<>(sourceFolderEntity, refilterFileEntity);
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
            if (this.fileService.isFileEntityDiffFromFile(fileEntityInDB, fileInFilesystem)) {
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

    private List<FileEntity> compareForFileCreatedMissingTrack(
            List<FileEntity> sourceFolderFileEntityList,
            List<FileSyncMappingEntity> fileSyncMappingEntityList) throws SyncDuoException {
        if (CollectionUtils.isEmpty(sourceFolderFileEntityList)) {
            return Collections.emptyList();
        }
        JoinResult<FileEntity, FileSyncMappingEntity> joinResult = JoinUtil.allJoin(
                sourceFolderFileEntityList,
                fileSyncMappingEntityList,
                FileEntity::getFileId,
                FileSyncMappingEntity::getSourceFileId
        );
        return joinResult.getLeftOuterResult();
    }

    private List<FileEntity> compareForFileChangedMissingTrack(
            List<FileEntity> sourceFolderFileEntityList,
            List<FileSyncMappingEntity> fileSyncMappingEntityList) throws SyncDuoException {
        if (CollectionUtils.isEmpty(sourceFolderFileEntityList) ||
                CollectionUtils.isEmpty(fileSyncMappingEntityList)) {
            return Collections.emptyList();
        }
        // 批量查询 destFileEntity
        Set<Long> destFileIdList = fileSyncMappingEntityList.stream()
                .map(FileSyncMappingEntity::getDestFileId)
                .collect(Collectors.toSet());
        List<FileEntity> destFileEntityList = this.fileService.getByFileIds(destFileIdList);
        if (CollectionUtils.isEmpty(destFileEntityList)) {
            return Collections.emptyList();
        }
        // 执行 match
        MatchResult<FileEntity, FileEntity> matchResult = MatchUtil.match(
                sourceFolderFileEntityList,
                destFileEntityList,
                fileSyncMappingEntityList,
                FileEntity::getFileId,
                FileEntity::getFileId,
                FileSyncMappingEntity::getSourceFileId,
                FileSyncMappingEntity::getDestFileId
        );
        List<ImmutablePair<FileEntity, FileEntity>> matched = matchResult.getMatched();
        if (CollectionUtils.isEmpty(matched)) {
            return Collections.emptyList();
        }
        List<FileEntity> result = new ArrayList<>(matched.size());
        for (ImmutablePair<FileEntity, FileEntity> pair : matched) {
            if (this.fileService.isSourceFileEntityDiffFromDest(pair.getLeft(), pair.getRight())) {
                result.add(pair.getLeft());
            }
        }
        return result;
    }

    private List<FileEntity> compareForFileDeletedMissingTrack(
            List<FileEntity> sourceFolderFileEntityList,
            List<FileSyncMappingEntity> fileSyncMappingEntityList) throws SyncDuoException {
        if (CollectionUtils.isEmpty(sourceFolderFileEntityList)) {
            return Collections.emptyList();
        }
        JoinResult<FileEntity, FileSyncMappingEntity> joinResult = JoinUtil.allJoin(
                sourceFolderFileEntityList,
                fileSyncMappingEntityList,
                FileEntity::getFileId,
                FileSyncMappingEntity::getSourceFileId
        );
        List<FileSyncMappingEntity> rightOuterResult = joinResult.getRightOuterResult();
        if (CollectionUtils.isEmpty(rightOuterResult)) {
            return Collections.emptyList();
        }
        Set<Long> sourceFileIds = rightOuterResult.stream()
                .map(FileSyncMappingEntity::getSourceFileId)
                .collect(Collectors.toSet());
        return this.fileService.getByFileIds(sourceFileIds);
    }
}
