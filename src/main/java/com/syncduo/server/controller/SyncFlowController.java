package com.syncduo.server.controller;

import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.DeleteSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowInfo;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowResponse;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.service.facade.SystemManagementService;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/sync-flow")
@Slf4j
public class SyncFlowController {
    private final FolderService folderService;

    private final SyncFlowService syncFlowService;

    private final SyncSettingService syncSettingService;

    private final FolderWatcher folderWatcher;

    private final FileSyncMappingService fileSyncMappingService;

    private final SystemManagementService systemManagementService;

    private final SystemConfigService systemConfigService;

    @Autowired
    public SyncFlowController(
            FolderService folderService,
            SyncFlowService syncFlowService,
            SyncSettingService syncSettingService,
            FolderWatcher folderWatcher,
            FileSyncMappingService fileSyncMappingService,
            SystemManagementService systemManagementService,
            SystemConfigService systemConfigService) {
        this.folderService = folderService;
        this.syncFlowService = syncFlowService;
        this.syncSettingService = syncSettingService;
        this.folderWatcher = folderWatcher;
        this.fileSyncMappingService = fileSyncMappingService;
        this.systemManagementService = systemManagementService;
        this.systemConfigService = systemConfigService;
    }

    @PostMapping("/add-sync-flow")
    public SyncFlowResponse addSyncFlow(@RequestBody CreateSyncFlowRequest createSyncFlowRequest) {
        // 参数检查
        try {
            this.checkAndCreateDestFolder(createSyncFlowRequest);
        } catch (SyncDuoException e) {
            // 删除 destFolder
            try {
                FilesystemUtil.deleteFolder(createSyncFlowRequest.getDestFolder());
            } catch (SyncDuoException ex) {
                log.error("addSyncFlow failed. can't delete folder: {}", createSyncFlowRequest.getDestFolder(), ex);
            }
            String errorMessage = "addSyncFlow failed, IllegalArgument " + e.getMessage();
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 folder entity
        FolderEntity sourceFolderEntity;
        FolderEntity destFolderEntity;
        try {
            sourceFolderEntity = this.folderService.createFolderEntity(createSyncFlowRequest.getSourceFolder());
            destFolderEntity = this.folderService.createFolderEntity(createSyncFlowRequest.getDestFolder());
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create folder entity." +
                    "sourceFolder is : %s.".formatted(createSyncFlowRequest.getDestFolder()) +
                    "destFolder is : %s.".formatted(createSyncFlowRequest.getSourceFolder());
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 syncFlow
        SyncFlowEntity syncFlowEntity;
        try {
            SyncFlowEntity dbResult = this.syncFlowService.getBySourceFolderIdAndDest(
                    sourceFolderEntity.getFolderId(),
                    destFolderEntity.getFolderId()
            );
            if (ObjectUtils.isNotEmpty(dbResult)) {
                return SyncFlowResponse.onSuccess("create sync flow success. already exist!");
            }
            syncFlowEntity = this.syncFlowService.createSyncFlow(
                    sourceFolderEntity.getFolderId(),
                    destFolderEntity.getFolderId(),
                    createSyncFlowRequest.getSyncFlowTypeEnum(),
                    createSyncFlowRequest.getSyncFlowName()
            );
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create syncFlowEntity." +
                    "sourceFolder is : %s.".formatted(createSyncFlowRequest.getDestFolder()) +
                    "destFolder is : %s.".formatted(createSyncFlowRequest.getSourceFolder());
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 syncSetting
        SyncSettingEntity syncSetting;
        try {
            syncSetting = this.syncSettingService.createSyncSetting(
                    syncFlowEntity.getSyncFlowId(),
                    createSyncFlowRequest.getFilters(),
                    createSyncFlowRequest.getSyncSettingEnum()
            );
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create sync setting." +
                    "filter is : %s.".formatted(createSyncFlowRequest.getFilterCriteria()) +
                    "sync setting is : %s.".formatted(createSyncFlowRequest.getSyncSetting());
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 watcher
        try {
            this.folderWatcher.addWatcher(sourceFolderEntity);
            this.folderWatcher.addWatcher(destFolderEntity);
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create watcher." +
                    "sourceFolder is : %s.".formatted(createSyncFlowRequest.getDestFolder()) +
                    "destFolder is : %s.".formatted(createSyncFlowRequest.getSourceFolder());
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // scan source folder 和 dest folder
        try {
            this.systemManagementService.updateFolderFromFileSystem(sourceFolderEntity.getFolderId());
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't scan folder." +
                    "source folder is %s".formatted(sourceFolderEntity);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        try {
            this.systemManagementService.updateFolderFromFileSystem(destFolderEntity.getFolderId());
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't scan folder." +
                    "dest folder is %s".formatted(destFolderEntity);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 设置返回信息
        SyncFlowInfo syncFlowInfo = SyncFlowInfo.builder()
                .syncFlowId(syncFlowEntity.getSyncFlowId().toString())
                .syncFlowName(syncFlowEntity.getSyncFlowName())
                .sourceFolderPath(sourceFolderEntity.getFolderFullPath())
                .destFolderPath(destFolderEntity.getFolderFullPath())
                .syncSettings(createSyncFlowRequest.getSyncSetting())
                .ignorePatten(createSyncFlowRequest.getFilterCriteria())
                .syncStatus(syncFlowEntity.getSyncStatus())
                .build();
        return SyncFlowResponse.onSuccess("创建 syncflow 成功", Collections.singletonList(syncFlowInfo));
    }

    @PostMapping("/delete-sync-flow")
    public SyncFlowResponse deleteSyncFlow(@RequestBody DeleteSyncFlowRequest deleteSyncFlowRequest) {
        Long syncFlowId = deleteSyncFlowRequest.getSyncFlowId();
        if (ObjectUtils.anyNull(deleteSyncFlowRequest, syncFlowId)) {
            String errorMessage = "deleteSyncFlow failed. " +
                    "deleteSyncFlowRequest or deleteSyncFlowRequest.SyncflowId is null";
            return this.generateSyncFlowErrorResponse(null, errorMessage);
        }
        // 获取 sync flow
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(syncFlowId);
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            SyncFlowInfo syncFlowInfo = SyncFlowInfo.builder()
                    .syncFlowId(syncFlowEntity.getSyncFlowId().toString())
                    .syncFlowName(syncFlowEntity.getSyncFlowName())
                    .build();
            return SyncFlowResponse.onSuccess("删除 syncflow 成功", Collections.singletonList(syncFlowInfo));
        }
        // 停止 watcher
        this.folderWatcher.stopMonitor(syncFlowEntity.getSourceFolderId());
        this.folderWatcher.stopMonitor(syncFlowEntity.getDestFolderId());
        // 删除 syncFlow
        try {
            this.syncFlowService.deleteSyncFlow(syncFlowEntity);
        } catch (SyncDuoException e) {
            String errorMessage = "deleteSyncFlow failed. deleteSyncFlow failed. " +
                    "deleteSyncFlowRequest is %s".formatted(deleteSyncFlowRequest);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 删除 sync setting
        try {
            this.syncSettingService.deleteSyncSetting(syncFlowId);
        } catch (SyncDuoException e) {
            String errorMessage = "deleteSyncFlow failed. deleteSyncSetting failed. " +
                    "deleteSyncFlowRequest is %s".formatted(deleteSyncFlowRequest);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 删除 file_sync_mapping
        try {
            this.fileSyncMappingService.deleteRecordBySyncFlowId(syncFlowId);
        } catch (SyncDuoException e) {
            String errorMessage = "deleteSyncFlow failed. deleteFileSyncMapping failed. " +
                    "deleteSyncFlowRequest is %s".formatted(deleteSyncFlowRequest);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        SyncFlowInfo syncFlowInfo = SyncFlowInfo.builder()
                .syncFlowId(syncFlowEntity.getSyncFlowId().toString())
                .syncFlowName(syncFlowEntity.getSyncFlowName())
                .build();
        return SyncFlowResponse.onSuccess("删除 syncFlow 成功", Collections.singletonList(syncFlowInfo));
    }

    private void checkAndCreateDestFolder(CreateSyncFlowRequest createSyncFlowRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(createSyncFlowRequest)) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "createSyncFlowRequest is null");
        }
        // 检查 syncflow name
        if (StringUtils.isBlank(createSyncFlowRequest.getSyncFlowName())) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "syncFlowName is null");
        }
        // 检查 sync setting
        if (StringUtils.isBlank(createSyncFlowRequest.getSyncSetting())) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "syncSetting is null");
        }
        try {
            SyncSettingEnum syncSettingEnum = SyncSettingEnum.valueOf(createSyncFlowRequest.getSyncSetting());
            createSyncFlowRequest.setSyncSettingEnum(syncSettingEnum);
        } catch (IllegalArgumentException e) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "sySetting: %s invalid.".formatted(createSyncFlowRequest.getSyncSetting()), e);
        }
        // 检查过滤条件
        List<String> filters;
        String filterCriteria = createSyncFlowRequest.getFilterCriteria();
        if (StringUtils.isNoneBlank(filterCriteria)) {
            try {
                filters = JsonUtil.deserializeStringToList(filterCriteria);
                createSyncFlowRequest.setFilters(filters);
            } catch (SyncDuoException e) {
                String errorMessage = "addSyncFlow failed. can't deserialize string to list. " +
                        "filterCriteria is %s".formatted(filterCriteria) + " error is " + e.getMessage();
                throw new SyncDuoException(errorMessage, e);
            }
        }
        // 检查 SyncFlowType
        if (StringUtils.isBlank(createSyncFlowRequest.getSyncFlowType())) {
            throw new SyncDuoException(
                    "isCreateSyncFlowRequestValid failed. syncFlowType is blank");
        }
        SyncFlowTypeEnum syncFlowTypeEnum;
        try {
            syncFlowTypeEnum = SyncFlowTypeEnum.valueOf(createSyncFlowRequest.getSyncFlowType());
            createSyncFlowRequest.setSyncFlowTypeEnum(syncFlowTypeEnum);
        } catch (IllegalArgumentException e) {
            throw new SyncDuoException(
                    "isCreateSyncFlowRequestValid failed. syncFlowType not valid." +
                            "syncFlowType is %s".formatted(createSyncFlowRequest.getSyncFlowType()));
        }
        // sync flow type 是 sync, 则从配置信息获取存储位置, destFolderPath = <storage_path>/random_name
        // 且 sync flow setting 是 MIRROR
        // 且 filterCriteria 是 []
        if (SyncFlowTypeEnum.SYNC.equals(syncFlowTypeEnum)) {
            SystemConfigEntity systemConfig = this.systemConfigService.getSystemConfig();
            if (ObjectUtils.isEmpty(systemConfig) || StringUtils.isBlank(systemConfig.getSyncStoragePath())) {
                throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                        "system config SyncStoragePath is null. " +
                        "system config is :%s".formatted(systemConfig));
            }
            String newFolderName = FilesystemUtil.getNewFolderName(
                    createSyncFlowRequest.getSourceFolderFullPath(),
                    systemConfig.getSyncStoragePath()
            );
            String destFolderPath = systemConfig.getSyncStoragePath() +
                    FilesystemUtil.getPathSeparator() +
                    newFolderName;
            createSyncFlowRequest.setDestFolderFullPath(destFolderPath);
            createSyncFlowRequest.setSyncSettingEnum(SyncSettingEnum.MIRROR);
            createSyncFlowRequest.setFilters(Collections.emptyList());
        }
        // 检查 sourceFolder 和 destFolder
        String sourceFolderFullPath = createSyncFlowRequest.getSourceFolderFullPath();
        String destFolderFullPath = createSyncFlowRequest.getDestFolderFullPath();
        if (StringUtils.isAnyBlank(sourceFolderFullPath, destFolderFullPath)) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "sourceFolderFullPath or destFolderFullPath is null");
        }
        // 判断 source folder 和 dest folder 是否相同
        if (sourceFolderFullPath.equals(destFolderFullPath)) {
            throw new SyncDuoException("addSyncFlow failed. " +
                    "source folder and dest folder is the same path. " +
                    "they are %s".formatted(sourceFolderFullPath));
        }
        // 检查 sourceFolderPath 路径是否存在
        Path sourceFolder = FilesystemUtil.isFolderPathValid(sourceFolderFullPath);
        createSyncFlowRequest.setSourceFolder(sourceFolder);
        // 检查 destFolderPath 路径是否存在, 不存在需要创建
        Path destFolder;
        if (FilesystemUtil.isFolderPathExist(destFolderFullPath)) {
            destFolder = FilesystemUtil.getFolder(destFolderFullPath);
        } else {
            destFolder = FilesystemUtil.createFolder(destFolderFullPath);
        }
        createSyncFlowRequest.setDestFolder(destFolder);
    }

    private SyncFlowResponse generateSyncFlowErrorResponse(SyncDuoException e, String errorMessage) {
        SyncFlowResponse syncFlowResponse = SyncFlowResponse.onError(errorMessage);
        log.error(errorMessage, e);
        return syncFlowResponse;
    }
}
