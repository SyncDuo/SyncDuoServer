package com.syncduo.server.controller;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncModeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.http.FolderStats;
import com.syncduo.server.model.http.syncflow.*;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.model.http.syncsettings.UpdateFilterCriteriaRequest;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.service.cache.SyncFlowServiceCache;
import com.syncduo.server.service.facade.SystemManagementService;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/sync-flow")
@Slf4j
@CrossOrigin
public class SyncFlowController {
    private final FolderService folderService;

    private final SyncFlowServiceCache syncFlowServiceCache;

    private final SyncSettingService syncSettingService;

    private final FolderWatcher folderWatcher;

    private final FileSyncMappingService fileSyncMappingService;

    private final SystemManagementService systemManagementService;

    private final SystemConfigService systemConfigService;

    @Autowired
    public SyncFlowController(
            FolderService folderService,
            SyncFlowServiceCache syncFlowServiceCache,
            SyncSettingService syncSettingService,
            FolderWatcher folderWatcher,
            FileSyncMappingService fileSyncMappingService,
            SystemManagementService systemManagementService,
            SystemConfigService systemConfigService) {
        this.folderService = folderService;
        this.syncFlowServiceCache = syncFlowServiceCache;
        this.syncSettingService = syncSettingService;
        this.folderWatcher = folderWatcher;
        this.fileSyncMappingService = fileSyncMappingService;
        this.systemManagementService = systemManagementService;
        this.systemConfigService = systemConfigService;
    }

    @PostMapping("/update-filter-criteria")
    public SyncFlowResponse updateFilterCriteria(
            @RequestBody UpdateFilterCriteriaRequest updateFilterCriteriaRequest) {
        // 参数检查
        if (ObjectUtils.isEmpty(updateFilterCriteriaRequest)) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. UpdateFilterCriteriaRequest is empty");
        }
        String filterCriteria = updateFilterCriteriaRequest.getFilterCriteria();
        if (StringUtils.isAnyBlank(
                filterCriteria,
                updateFilterCriteriaRequest.getSyncFlowId())) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. " +
                    "SyncSettingId or FilterCriteria or SyncFlowId is empty");
        }
        long syncFlowId;
        try {
            syncFlowId = Long.parseLong(updateFilterCriteriaRequest.getSyncFlowId());
        } catch (NumberFormatException e) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. syncSettingId convert to long failed." +
                    e.getMessage());
        }
        try {
            JsonUtil.deserializeStringToList(filterCriteria);
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. filterCriteria convert to list failed." +
                    e.getMessage());
        }
        // pause 的 sync flow, 才能 update filter criteria
        SyncFlowEntity syncFlowEntity;
        try {
            syncFlowEntity = this.syncFlowServiceCache.getBySyncFlowId(syncFlowId);
            if (ObjectUtils.isEmpty(syncFlowEntity)) {
                return SyncFlowResponse.onSuccess("updateFilterCriteria success. SyncFlow is deleted");
            }
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. can't get syncFlowEntity." +
                    e.getMessage());
        }
        if (!SyncFlowStatusEnum.PAUSE.name().equals(syncFlowEntity.getSyncStatus())) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. sync flow status is not PAUSE");
        }
        // 查找 syncSetting
        try {
            SyncSettingEntity syncSettingEntity = this.syncSettingService.getBySyncFlowId(syncFlowId);
            if (ObjectUtils.isEmpty(syncSettingEntity)) {
                return SyncFlowResponse.onSuccess("updateFilterCriteria success. SyncSetting is deleted");
            }
            syncSettingEntity.setFilterCriteria(filterCriteria);
            this.syncSettingService.updateById(syncSettingEntity);
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("updateFilterCriteria failed. can't update syncSettings." +
                    e.getMessage());
        }
        return SyncFlowResponse.onSuccess("updateFilterCriteria success");
    }

    @PostMapping("/change-sync-flow-status")
    public SyncFlowResponse changeSyncFlowStatus(
            @RequestBody ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) {
        // 参数检查
        if (ObjectUtils.isEmpty(changeSyncFlowStatusRequest)) {
            return SyncFlowResponse.onError("changeSyncFlowStatus failed, changeSyncFlowStatus is empty");
        }
        if (StringUtils.isBlank(changeSyncFlowStatusRequest.getSyncFlowId())) {
            return SyncFlowResponse.onError("changeSyncFlowStatus failed. syncFlowId is blank");
        }
        long syncFlowId;
        try {
            syncFlowId = Long.parseLong(changeSyncFlowStatusRequest.getSyncFlowId());
        } catch (NumberFormatException e) {
            return SyncFlowResponse.onError("changeSyncFlowStatus failed. syncFlowId convert to long failed." +
                    e.getMessage());
        }
        SyncFlowStatusEnum syncFlowStatusEnum;
        try {
            syncFlowStatusEnum = SyncFlowStatusEnum.valueOf(changeSyncFlowStatusRequest.getSyncFlowStatus());
        } catch (IllegalArgumentException e) {
            return SyncFlowResponse.onError("changeSyncFlowStatus failed." +
                    "syncFlowStatus convert to enum failed" + e.getMessage());
        }
        // 获取需要更改状态的 sync flow
        SyncFlowEntity syncFlowEntity;
        try {
            syncFlowEntity = this.syncFlowServiceCache.getBySyncFlowId(syncFlowId);
            if (ObjectUtils.isEmpty(syncFlowEntity)) {
                return SyncFlowResponse.onSuccess("changeSyncFlowStatus success.");
            }
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("changeSyncFlowStatus failed. get syncflow failed." +
                    e.getMessage());
        }
        // 更改状态
        SyncFlowResponse syncFlowResponse = this.changeSyncFlowStatusInner(
                syncFlowStatusEnum,
                syncFlowEntity,
                syncFlowId
        );
        if (ObjectUtils.isNotEmpty(syncFlowResponse)) return syncFlowResponse;
        return SyncFlowResponse.onSuccess("changeSyncFlowStatus success.");
    }

    @PostMapping("/change-all-sync-flow-status")
    public SyncFlowResponse changeAllSyncFlowStatus(
            @RequestBody ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) {
        // 参数检查
        if (ObjectUtils.isEmpty(changeSyncFlowStatusRequest)) {
            return SyncFlowResponse.onError("changeAllSyncFlowStatus failed, changeSyncFlowStatus request is empty");
        }
        SyncFlowStatusEnum syncFlowStatusEnum;
        try {
            syncFlowStatusEnum = SyncFlowStatusEnum.valueOf(changeSyncFlowStatusRequest.getSyncFlowStatus());
        } catch (IllegalArgumentException e) {
            return SyncFlowResponse.onError("changeAllSyncFlowStatus failed." +
                    "syncFlowStatus convert to enum failed" + e.getMessage());
        }
        List<SyncFlowEntity> syncFlowEntities = this.syncFlowServiceCache.getAllSyncFlow();
        if (CollectionUtils.isEmpty(syncFlowEntities)) {
            return SyncFlowResponse.onSuccess("changeAllSyncFlowStatus success.");
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntities) {
            Long syncFlowId = syncFlowEntity.getSyncFlowId();
            SyncFlowResponse syncFlowResponse = this.changeSyncFlowStatusInner(
                    syncFlowStatusEnum,
                    syncFlowEntity,
                    syncFlowId
            );
            if (ObjectUtils.isNotEmpty(syncFlowResponse)) return syncFlowResponse;
        }
        return SyncFlowResponse.onSuccess("changeSyncFlowStatus success.");
    }

    private SyncFlowResponse changeSyncFlowStatusInner(
            SyncFlowStatusEnum syncFlowStatusEnum,
            SyncFlowEntity syncFlowEntity,
            Long syncFlowId) {
        try {
            switch (syncFlowStatusEnum) {
                case PAUSE -> this.syncFlowServiceCache.updateSyncFlowStatus(syncFlowId, SyncFlowStatusEnum.PAUSE);
                case RESUME -> {
                    // PAUSE 的 sync flow 才能 RESUME
                    if (!SyncFlowStatusEnum.PAUSE.name().equals(syncFlowEntity.getSyncStatus())) {
                        return null;
                    }
                    boolean isChanged = this.systemManagementService.resumeSyncFlow(syncFlowEntity);
                    if (isChanged) {
                        this.syncFlowServiceCache.updateSyncFlowStatus(syncFlowId, SyncFlowStatusEnum.NOT_SYNC);
                    } else {
                        this.syncFlowServiceCache.updateSyncFlowStatus(syncFlowId, SyncFlowStatusEnum.SYNC);
                    }
                }
                case RESCAN -> {
                    // SYNC 的 sync flow 才能 rescan
                    if (!SyncFlowStatusEnum.SYNC.name().equals(syncFlowEntity.getSyncStatus())) {
                        break;
                    }
                    boolean sourceFolderUpdated = this.systemManagementService.updateFolderFromFileSystem(
                            syncFlowEntity.getSourceFolderId());
                    if (sourceFolderUpdated) {
                        this.syncFlowServiceCache.updateSyncFlowStatus(
                                syncFlowEntity, SyncFlowStatusEnum.NOT_SYNC);
                    } else {
                        this.syncFlowServiceCache.updateSyncFlowStatus(
                                syncFlowEntity, SyncFlowStatusEnum.SYNC);
                    }
                    this.systemManagementService.updateFolderFromFileSystem(syncFlowEntity.getDestFolderId());
                }
            }
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("changeSyncFlowStatus failed. " + e.getMessage());
        }
        return null;
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
        boolean isSourceFolderNew = true;
        FolderEntity sourceFolderEntity;
        FolderEntity destFolderEntity;
        Path sourceFolder = createSyncFlowRequest.getSourceFolder();
        try {
            // 检查 sourceFolderEntity 是否存在, 影响后续初始化动作
            sourceFolderEntity = this.folderService.getByFolderFullPath(sourceFolder.toAbsolutePath().toString());
            if (ObjectUtils.isEmpty(sourceFolderEntity)) {
                sourceFolderEntity = this.folderService.createFolderEntity(sourceFolder);
            } else {
                isSourceFolderNew = false;
            }
            destFolderEntity = this.folderService.createFolderEntity(createSyncFlowRequest.getDestFolder());
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create folder entity." +
                    "sourceFolder is : %s.".formatted(createSyncFlowRequest.getDestFolder()) +
                    "destFolder is : %s.".formatted(sourceFolder);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 syncFlow
        SyncFlowEntity syncFlowEntity;
        try {
            SyncFlowEntity dbResult = this.syncFlowServiceCache.getBySourceFolderIdAndDest(
                    sourceFolderEntity.getFolderId(),
                    destFolderEntity.getFolderId()
            );
            if (ObjectUtils.isNotEmpty(dbResult)) {
                return SyncFlowResponse.onSuccess("create sync flow success. already exist!");
            }
            syncFlowEntity = this.syncFlowServiceCache.createSyncFlow(
                    sourceFolderEntity.getFolderId(),
                    destFolderEntity.getFolderId(),
                    createSyncFlowRequest.getSyncFlowTypeEnum(),
                    createSyncFlowRequest.getSyncFlowName()
            );
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create syncFlowEntity." +
                    "sourceFolder is : %s.".formatted(createSyncFlowRequest.getDestFolder()) +
                    "destFolder is : %s.".formatted(sourceFolder);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 syncSetting
        SyncSettingEntity syncSetting;
        try {
            syncSetting = this.syncSettingService.createSyncSetting(
                    syncFlowEntity.getSyncFlowId(),
                    createSyncFlowRequest.getFilters(),
                    createSyncFlowRequest.getSyncModeEnum()
            );
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create sync setting." +
                    "filter is : %s.".formatted(createSyncFlowRequest.getFilterCriteria()) +
                    "sync setting is : %s.".formatted(createSyncFlowRequest.getSyncMode());
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 创建 watcher
        try {
            this.folderWatcher.addWatcher(sourceFolderEntity);
            this.folderWatcher.addWatcher(destFolderEntity);
        } catch (SyncDuoException e) {
            String errorMessage = "addSyncFlow failed. can't create watcher." +
                    "sourceFolder is : %s.".formatted(createSyncFlowRequest.getDestFolder()) +
                    "destFolder is : %s.".formatted(sourceFolder);
            return this.generateSyncFlowErrorResponse(e, errorMessage);
        }
        // 扫描 source folder 和 dest folder
        try {
            if (!isSourceFolderNew) {
                this.systemManagementService.sendDownStreamEventFromSourceFolder(syncFlowEntity);
            } else {
                this.systemManagementService.updateFolderFromFileSystem(sourceFolderEntity.getFolderId());
            }
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
                .syncMode(createSyncFlowRequest.getSyncMode())
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
        SyncFlowEntity syncFlowEntity = this.syncFlowServiceCache.getById(syncFlowId);
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
            this.syncFlowServiceCache.deleteSyncFlow(syncFlowEntity);
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

    @GetMapping("/get-sync-flow")
    public SyncFlowResponse getSyncFlow() {
        // 查询全部 sync-flow
        List<SyncFlowEntity> allSyncFlow = this.syncFlowServiceCache.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return SyncFlowResponse.onSuccess("没有正在运行的 sync flow", null);
        }
        // 设置返回结果
        List<SyncFlowInfo> syncFlowInfoList = new ArrayList<>();
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            FolderEntity sourceFolderEntity = this.folderService.getById(syncFlowEntity.getSourceFolderId());
            FolderEntity destFolderEntity = this.folderService.getById(syncFlowEntity.getDestFolderId());
            // 获取 syncflow type
            SyncFlowTypeEnum syncFlowTypeEnum = SyncFlowTypeEnum.getByString(syncFlowEntity.getSyncFlowType());
            if (ObjectUtils.isEmpty(syncFlowTypeEnum)) {
                return SyncFlowResponse.onError("获取 sync flow 失败. 异常信息 "
                        + "sync flow type " + syncFlowEntity.getSyncFlowType() + "无法识别");
            }
            // 获取同步设置
            SyncSettingEntity syncSettingEntity;
            try {
                syncSettingEntity = this.syncSettingService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
            } catch (SyncDuoException e) {
                return SyncFlowResponse.onError("获取 sync flow 失败. 异常信息 " + e.getMessage());
            }
            SyncModeEnum syncModeEnum = SyncModeEnum.getByCode(syncSettingEntity.getSyncMode());
            if (ObjectUtils.isEmpty(syncSettingEntity) || syncModeEnum == null) {
                return SyncFlowResponse.onError("获取 sync flow 失败. 异常信息 "
                        + "sync mode " + syncSettingEntity.getSyncMode() + "无法识别");
            }
            // 获取文件夹信息
            FolderStats folderStats;
            try {
                List<Long> folderInfo = FilesystemUtil.getFolderInfo(destFolderEntity.getFolderFullPath());
                folderStats = new FolderStats(folderInfo.get(0), folderInfo.get(1), folderInfo.get(2));
            } catch (SyncDuoException e) {
                return SyncFlowResponse.onError("获取 sync flow 失败. 异常信息 " + e.getMessage());
            }
            // 处理 lastSyncTime 为空的现象, 并格式化
            String lastSyncTime;
            if (ObjectUtils.isEmpty(syncFlowEntity.getLastSyncTime())) {
                lastSyncTime = "";
            } else {
                lastSyncTime = syncFlowEntity.getLastSyncTime().toString();
            }
            SyncFlowInfo syncFlowInfo = SyncFlowInfo.builder()
                    .syncFlowId(syncFlowEntity.getSyncFlowId().toString())
                    .syncFlowName(syncFlowEntity.getSyncFlowName())
                    .sourceFolderPath(sourceFolderEntity.getFolderFullPath())
                    .destFolderPath(destFolderEntity.getFolderFullPath())
                    .syncMode(syncModeEnum.name())
                    .ignorePatten(syncSettingEntity.getFilterCriteria())
                    .syncFlowType(syncFlowTypeEnum.name())
                    .destFolderStats(folderStats)
                    .syncStatus(syncFlowEntity.getSyncStatus())
                    .lastSyncTimeStamp(lastSyncTime)
                    .build();
            syncFlowInfoList.add(syncFlowInfo);
        }
        return SyncFlowResponse.onSuccess("创建 syncflow 成功", syncFlowInfoList);
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
        if (StringUtils.isBlank(createSyncFlowRequest.getSyncMode())) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "syncSetting is null");
        }
        // 获取 syncMode
        try {
            SyncModeEnum syncModeEnum = SyncModeEnum.valueOf(createSyncFlowRequest.getSyncMode());
            createSyncFlowRequest.setSyncModeEnum(syncModeEnum);
        } catch (IllegalArgumentException e) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "sySetting: %s invalid.".formatted(createSyncFlowRequest.getSyncMode()), e);
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
        // 且 sync flow setting 只能是 MIRROR
        // 且 filterCriteria 只能是 []
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
            createSyncFlowRequest.setSyncModeEnum(SyncModeEnum.MIRROR);
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
