package com.syncduo.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.DeleteSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowResponse;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.bus.FileAccessValidator;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.service.facade.FileOperationService;
import com.syncduo.server.service.bussiness.impl.FolderService;
import com.syncduo.server.service.bussiness.impl.SyncFlowService;
import com.syncduo.server.service.bussiness.impl.SyncSettingService;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/sync-flow")
@Slf4j
public class SyncFlowController {
    private final FolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final FileOperationService fileOpService;

    private final SyncSettingService syncSettingService;

    private final FolderWatcher folderWatcher;

    private final FileAccessValidator fileAccessValidator;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> LIST_STRING_TYPE_REFERENCE = new TypeReference<>() {};

    @Autowired
    public SyncFlowController(
            FolderService rootFolderService,
            SyncFlowService syncFlowService,
            FileOperationService fileOpService,
            SyncSettingService syncSettingService,
            FolderWatcher folderWatcher,
            FileAccessValidator fileAccessValidator) {
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileOpService = fileOpService;
        this.syncSettingService = syncSettingService;
        this.folderWatcher = folderWatcher;
        this.fileAccessValidator = fileAccessValidator;
    }

    @PostMapping("/add-sync-flow")
    public SyncFlowResponse addSyncFlow(@RequestBody CreateSyncFlowRequest createSyncFlowRequest) {
        // 参数检查
        try {
            this.isCreateSyncFlowRequestValid(createSyncFlowRequest);
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("addSource2ContentSyncFlow failed, IllegalArgument " + e.getMessage());
        }
        // 检查过滤条件
        List<String> filters = new ArrayList<>();
        String filterCriteria = createSyncFlowRequest.getFilterCriteria();
        if (StringUtils.isNoneBlank(filterCriteria)) {
            try {
                filters = OBJECT_MAPPER.readValue(filterCriteria, LIST_STRING_TYPE_REFERENCE);
            } catch (JsonProcessingException e) {
                SyncFlowResponse syncFlowResponse = SyncFlowResponse.onError(
                        "addSource2ContentSyncFlow failed. can't deserialize string to list. " +
                        "string is %s".formatted(filterCriteria) + " error is " + e.getMessage());
                log.error(syncFlowResponse.toString());
                return syncFlowResponse;
            }
        }
        // 判断 source folder 和 dest folder 是否相同
        if (createSyncFlowRequest.getSourceFolderFullPath().equals(createSyncFlowRequest.getDestFolderFullPath())) {
            SyncFlowResponse syncFlowResponse = SyncFlowResponse.onError(("addSource2ContentSyncFlow failed. " +
                    "source folder and dest folder is the same path. " +
                    "they are %s").formatted(createSyncFlowRequest.getSourceFolderFullPath()));
            log.error(syncFlowResponse.toString());
            return syncFlowResponse;
        }
        // 判断 sync-flow 是否已经存在
        try {
            int resultCode = this.isSyncFlowExist(createSyncFlowRequest);
            switch (resultCode) {
                case 0 -> {
                    List<Long> rootFolderIdList = firstTimeCreateSourceFolder(createSyncFlowRequest, filters);
                    SyncFlowResponse syncFlowResponse = SyncFlowResponse.onSuccess("sync-flow created successes");
                    syncFlowResponse.setData(rootFolderIdList);
                    log.info(syncFlowResponse.toString());
                    return syncFlowResponse;
                }
                case 1 -> {
                    List<Long> rootFolderIdList = this.createContentSyncFlow(createSyncFlowRequest, filters);
                    SyncFlowResponse syncFlowResponse = SyncFlowResponse.onSuccess("sync-flow created successes");
                    syncFlowResponse.setData(rootFolderIdList);
                    log.info(syncFlowResponse.toString());
                    return syncFlowResponse;
                }
                case 2 -> {
                    SyncFlowResponse syncFlowResponse = SyncFlowResponse.onSuccess("sync-flow exist");
                    log.info(syncFlowResponse.toString());
                    return syncFlowResponse;
                }
                default -> throw new SyncDuoException("addSource2ContentSyncFlow failed. " +
                        "unrecognized result code : " + resultCode);
            }
        } catch (SyncDuoException e) {
            log.error("addSource2ContentSyncFlow failed.", e);
            return SyncFlowResponse.onError("create sync-flow failed " + e.getMessage());
        }
    }

    @PostMapping("/delete-sync-flow")
    public SyncFlowResponse deleteSyncFlow(@RequestBody DeleteSyncFlowRequest deleteSyncFlowRequest) {
        if (ObjectUtils.anyNull(
                deleteSyncFlowRequest,
                deleteSyncFlowRequest.getSource2InternalSyncFlowId(),
                deleteSyncFlowRequest.getInternal2ContentSyncFlowId())) {
            return SyncFlowResponse.onError("deleteSyncFlow failed. " +
                    "deleteSyncFlowRequest or source2InternalSyncFlowId or internal2ContentSyncFlowId is null");
        }
        Long source2InternalSyncFlowId = deleteSyncFlowRequest.getSource2InternalSyncFlowId();
        SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.getById(source2InternalSyncFlowId);
        if (ObjectUtils.isEmpty(source2InternalSyncFlow)) {
            return SyncFlowResponse.onError("deleteSyncFlow failed. " +
                    "can't find the syncFlowEntity by id %s".formatted(source2InternalSyncFlowId));
        }
        Long internal2ContentSyncFlowId = deleteSyncFlowRequest.getInternal2ContentSyncFlowId();
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.getById(internal2ContentSyncFlowId);
        if (ObjectUtils.isEmpty(internal2ContentSyncFlow)) {
            return SyncFlowResponse.onError("deleteSyncFlow failed. " +
                    "can't find the syncFlowEntity by id %s".formatted(internal2ContentSyncFlow));
        }
        try {
            // delete sync-flow
            this.deleteSyncFlow(source2InternalSyncFlowId);
            this.deleteSyncFlow(internal2ContentSyncFlowId);
            // 去除 FileAccessValidator
            this.fileAccessValidator.removeWhitelist(source2InternalSyncFlowId, internal2ContentSyncFlowId);
        } catch (SyncDuoException e) {
            log.error("deleteSyncFlow failed. deleteSyncFlowRequest is {}.", deleteSyncFlowRequest, e);
            return SyncFlowResponse.onError("deleteSyncFlow failed. exception is %s".formatted(e));
        }
        return SyncFlowResponse.onSuccess("deleteSyncFlow success.");
    }

    private void deleteSyncFlow(Long syncFlowId) throws SyncDuoException {
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(syncFlowId);
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new SyncDuoException("deleteSyncFlow failed. can't find sync-flow by id %s".formatted(syncFlowId));
        }
        SyncFlowTypeEnum syncFlowType = SyncFlowTypeEnum.getByString(syncFlowEntity.getSyncFlowType());
        if (ObjectUtils.isEmpty(syncFlowType)) {
            throw new SyncDuoException(("deletedSyncFlow failed. " +
                    "can't get syncFlowType by syncFlowEntity %s").formatted(syncFlowEntity));
        }
        // 删除数据库记录
        this.syncFlowService.deleteSyncFlow(syncFlowEntity);
        // 根据 sync-flow, 停止 watcher
        if (syncFlowType.equals(SyncFlowTypeEnum.SOURCE_TO_INTERNAL)) {
            this.folderWatcher.stopMonitor(syncFlowEntity.getSourceFolderId());
        } else if (syncFlowType.equals(SyncFlowTypeEnum.INTERNAL_TO_CONTENT)) {
            this.folderWatcher.stopMonitor(syncFlowEntity.getDestFolderId());
        } else {
            throw new SyncDuoException("deleteSyncFlow failed. wrong syncFlowTypeEnum %s".formatted(syncFlowEntity));
        }
    }

    private List<Long> firstTimeCreateSourceFolder(CreateSyncFlowRequest createSyncFlowRequest, List<String> filters)
            throws SyncDuoException {
        String sourceFolderFullPath = createSyncFlowRequest.getSourceFolderFullPath();
        // 创建 source folder 和 internal folder
        Pair<FolderEntity, FolderEntity> sourceAndInternalFolderEntity =
                this.rootFolderService.createSourceFolder(sourceFolderFullPath);
        FolderEntity sourceFolderEntity = sourceAndInternalFolderEntity.getLeft();
        FolderEntity internalFolderEntity = sourceAndInternalFolderEntity.getRight();
        // 创建 content folder
        FolderEntity contentFolderEntity =
                this.rootFolderService.createContentFolder(
                        sourceFolderFullPath,
                        createSyncFlowRequest.getDestFolderFullPath());
        // 创建 source to internal sync flow
        SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.createSyncFlow(
                sourceFolderEntity.getFolderId(),
                internalFolderEntity.getFolderId(),
                SyncFlowTypeEnum.SOURCE_TO_INTERNAL);
        // 创建 internal to content sync flow
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                internalFolderEntity.getFolderId(),
                contentFolderEntity.getFolderId(),
                SyncFlowTypeEnum.INTERNAL_TO_CONTENT
        );
        // 添加 FileAccessValidator 白名单
        this.fileAccessValidator.addWhitelist(sourceFolderEntity, internalFolderEntity, contentFolderEntity);
        // 创建 sync setting
        SyncSettingEntity syncSettingEntity = this.syncSettingService.createSyncSetting(
                internal2ContentSyncFlow.getSyncFlowId(),
                filters,
                createSyncFlowRequest.getFlattenFolder() ? SyncSettingEnum.FLATTEN_FOLDER : SyncSettingEnum.MIRROR
        );
        // 执行 init scan 任务
        this.fileOpService.initialScan(sourceFolderEntity);
        // 执行 addWatcher
        this.folderWatcher.addWatcher(sourceFolderEntity);
        this.folderWatcher.addWatcher(contentFolderEntity);
        // 返回 source2InternalSyncFlowId, internal2ContentSyncFlowId
        return List.of(
                source2InternalSyncFlow.getSyncFlowId(),
                internal2ContentSyncFlow.getSyncFlowId());
    }

    private List<Long> createContentSyncFlow(CreateSyncFlowRequest createSyncFlowRequest, List<String> filters)
            throws SyncDuoException {
        // 获取 source 和 internal folder entity
        Pair<FolderEntity, FolderEntity> sourceInternalFolderPair =
                this.rootFolderService.getFolderPairByPath(createSyncFlowRequest.getSourceFolderFullPath());
        // 获取 source2Internal sync-flow
        SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.getSourceSyncFlowByFolderId(
                sourceInternalFolderPair.getLeft().getFolderId());
        // 创建 content folder
        FolderEntity contentFolderEntity = this.rootFolderService.createContentFolder(
                createSyncFlowRequest.getSourceFolderFullPath(),
                createSyncFlowRequest.getDestFolderFullPath()
        );
        // 创建 sync-flow
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                sourceInternalFolderPair.getRight().getFolderId(),
                contentFolderEntity.getFolderId(),
                SyncFlowTypeEnum.INTERNAL_TO_CONTENT
        );
        // 添加 FileAccessValidator 白名单
        this.fileAccessValidator.addWhitelist(contentFolderEntity);
        // 创建 sync setting
        SyncSettingEntity syncSettingEntity = this.syncSettingService.createSyncSetting(
                internal2ContentSyncFlow.getSyncFlowId(),
                filters,
                createSyncFlowRequest.getFlattenFolder() ? SyncSettingEnum.FLATTEN_FOLDER : SyncSettingEnum.MIRROR
        );
        // 执行 init scan 任务
        this.fileOpService.initialScan(contentFolderEntity);
        // 执行 addWatcher
        this.folderWatcher.addWatcher(contentFolderEntity);
        // 返回 source2InternalSyncFlowId, internal2ContentSyncFlowId
        return List.of(
                source2InternalSyncFlow.getSyncFlowId(),
                internal2ContentSyncFlow.getSyncFlowId());
    }


    // 0: source folder -> dest folder 不存在
    // 1: source folder -> internal folder 存在, dest folder 不存在
    // 2: source folder -> dest folder 存在
    private int isSyncFlowExist(CreateSyncFlowRequest createSyncFlowRequest) throws SyncDuoException {
        // 获取 source folder full path 和 dest folder full path
        String sourceFolderFullPath = createSyncFlowRequest.getSourceFolderFullPath();
        String destFolderFullPath = createSyncFlowRequest.getDestFolderFullPath();
        // 检查 source folder, internal folder, dest folder 是否已存在
        Pair<FolderEntity, FolderEntity> sourceInternalFolderEntityPair =
                this.rootFolderService.getFolderPairByPath(sourceFolderFullPath);
        FolderEntity destFolderEntity = this.rootFolderService.getByFolderFullPath(destFolderFullPath);
        if (ObjectUtils.isEmpty(sourceInternalFolderEntityPair)) {
            if (ObjectUtils.isEmpty(destFolderEntity)) {
                return 0;
            } else {
                throw new SyncDuoException("isSyncFlowExist failed. " +
                        "source and internal folder not exist, but dest folder exist");
            }
        }
        // source, internal 存在, 则判断 dest folder 是否存在, 不存在说明 sync-flow 也不存在
        if (ObjectUtils.isEmpty(destFolderEntity)) {
            return 1;
        }
        // 判断对应的 sync flow 是否已存在
        FolderEntity internalFolderEntity = sourceInternalFolderEntityPair.getRight();
        List<SyncFlowEntity> internalSyncFlowList =
                this.syncFlowService.getInternalSyncFlowByFolderId(internalFolderEntity.getFolderId());
        for (SyncFlowEntity internalSyncFlowEntity : internalSyncFlowList) {
            if (internalSyncFlowEntity.getDestFolderId().equals(destFolderEntity.getFolderId())) {
                return 2;
            }
        }
        return 1;
    }

    private void isCreateSyncFlowRequestValid(CreateSyncFlowRequest createSyncFlowRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(createSyncFlowRequest)) {
            throw new SyncDuoException("isSyncFlowRequestValid failed. " +
                    "syncFlowRequest is null");
        }
        if (ObjectUtils.anyNull(
                createSyncFlowRequest.getFlattenFolder())) {
            throw new SyncDuoException(
                    "isSyncFlowRequestValid failed. concatDestFolderPath is null");
        }
        String sourceFolderFullPath = createSyncFlowRequest.getSourceFolderFullPath();
        String destParentFolderFullPath = createSyncFlowRequest.getDestParentFolderFullPath();
        if (StringUtils.isAnyBlank(
                sourceFolderFullPath,
                createSyncFlowRequest.getDestParentFolderFullPath()
        )) {
            throw new SyncDuoException("isSyncFlowRequestValid failed. " +
                    "sourceFolderFullPath or destParentFolderFullPath is null");
        }
        // 检查 sourceFolderPath 路径是否正确
        Path sourceFolder = FilesystemUtil.isFolderPathValid(sourceFolderFullPath);
        // 检查 destParentFolderFullPath 路径是否正确
        FilesystemUtil.isFolderPathValid(destParentFolderFullPath);
        // 按要求拼接 destFolderFullPath
        String destFolderName = createSyncFlowRequest.getDestFolderName();
        if (StringUtils.isBlank(destFolderName)) {
            destFolderName = sourceFolder.getFileName().toString();
        }
        String destFolderFullPath = destParentFolderFullPath + FilesystemUtil.getPathSeparator() + destFolderName;
        if (FilesystemUtil.isFolderPathExist(destFolderFullPath)) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. destFolderFullPath already exist");
        }
        createSyncFlowRequest.setDestFolderFullPath(destFolderFullPath);
    }
}
