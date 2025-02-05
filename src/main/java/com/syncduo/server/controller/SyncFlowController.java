package com.syncduo.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.DeleteSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowInfo;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.mq.FileAccessValidator;
import com.syncduo.server.mq.producer.RootFolderEventProducer;
import com.syncduo.server.service.impl.AdvancedFileOpService;
import com.syncduo.server.service.impl.RootFolderService;
import com.syncduo.server.service.impl.SyncFlowService;
import com.syncduo.server.service.impl.SyncSettingService;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/sync-flow")
@CrossOrigin
@Slf4j
public class SyncFlowController {
    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final AdvancedFileOpService fileOpService;

    private final SyncSettingService syncSettingService;

    private final RootFolderEventProducer rootFolderEventProducer;

    private final FileAccessValidator fileAccessValidator;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> LIST_STRING_TYPE_REFERENCE = new TypeReference<>() {};

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    private static final double MB = 1024.0 * 1024.0;

    private static final double KB = 1024.0;

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.00");

    @Autowired
    public SyncFlowController(
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            AdvancedFileOpService fileOpService,
            SyncSettingService syncSettingService,
            RootFolderEventProducer rootFolderEventProducer,
            FileAccessValidator fileAccessValidator) {
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileOpService = fileOpService;
        this.syncSettingService = syncSettingService;
        this.rootFolderEventProducer = rootFolderEventProducer;
        this.fileAccessValidator = fileAccessValidator;
    }

    @GetMapping("/get-sync-flow")
    public SyncFlowResponse getAllSyncFlow() {
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getBySyncFlowType(SyncFlowTypeEnum.INTERNAL_TO_CONTENT);
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return SyncFlowResponse.onSuccess("No Active SyncFlow");
        }
        try {
            List<SyncFlowInfo> syncFlowInfoList = new ArrayList<>();
            for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                SyncFlowInfo syncFlowInfo = this.toSyncFlowInfo(syncFlowEntity);
                syncFlowInfoList.add(syncFlowInfo);
            }
            return SyncFlowResponse.onSuccess("success", syncFlowInfoList);
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("transform to SyncFlowInfo failed. exception is " + e);
        }
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
                    syncFlowResponse.setSyncFlowIds(rootFolderIdList);
                    log.info(syncFlowResponse.toString());
                    return syncFlowResponse;
                }
                case 1 -> {
                    List<Long> rootFolderIdList = this.createContentSyncFlow(createSyncFlowRequest, filters);
                    SyncFlowResponse syncFlowResponse = SyncFlowResponse.onSuccess("sync-flow created successes");
                    syncFlowResponse.setSyncFlowIds(rootFolderIdList);
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
            this.rootFolderEventProducer.stopMonitor(syncFlowEntity.getSourceFolderId());
        } else if (syncFlowType.equals(SyncFlowTypeEnum.INTERNAL_TO_CONTENT)) {
            this.rootFolderEventProducer.stopMonitor(syncFlowEntity.getDestFolderId());
        } else {
            throw new SyncDuoException("deleteSyncFlow failed. wrong syncFlowTypeEnum %s".formatted(syncFlowEntity));
        }
    }

    private List<Long> firstTimeCreateSourceFolder(CreateSyncFlowRequest createSyncFlowRequest, List<String> filters)
            throws SyncDuoException {
        String sourceFolderFullPath = createSyncFlowRequest.getSourceFolderFullPath();
        // 创建 source folder 和 internal folder
        Pair<RootFolderEntity, RootFolderEntity> sourceAndInternalFolderEntity =
                this.rootFolderService.createSourceFolder(sourceFolderFullPath);
        RootFolderEntity sourceFolderEntity = sourceAndInternalFolderEntity.getLeft();
        RootFolderEntity internalFolderEntity = sourceAndInternalFolderEntity.getRight();
        // 创建 content folder
        RootFolderEntity contentFolderEntity =
                this.rootFolderService.createContentFolder(
                        sourceFolderFullPath,
                        createSyncFlowRequest.getDestFolderFullPath());
        // 创建 source to internal sync flow
        SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.createSyncFlow(
                createSyncFlowRequest.getSyncFlowName(),
                sourceFolderEntity.getRootFolderId(),
                internalFolderEntity.getRootFolderId(),
                SyncFlowTypeEnum.SOURCE_TO_INTERNAL);
        // 创建 internal to content sync flow
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                createSyncFlowRequest.getSyncFlowName(),
                internalFolderEntity.getRootFolderId(),
                contentFolderEntity.getRootFolderId(),
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
        this.rootFolderEventProducer.addMonitor(sourceFolderEntity);
        this.rootFolderEventProducer.addMonitor(contentFolderEntity);
        // 返回 source2InternalSyncFlowId, internal2ContentSyncFlowId
        return List.of(
                source2InternalSyncFlow.getSyncFlowId(),
                internal2ContentSyncFlow.getSyncFlowId());
    }

    private List<Long> createContentSyncFlow(CreateSyncFlowRequest createSyncFlowRequest, List<String> filters)
            throws SyncDuoException {
        // 获取 source 和 internal folder entity
        Pair<RootFolderEntity, RootFolderEntity> sourceInternalFolderPair =
                this.rootFolderService.getFolderPairByPath(createSyncFlowRequest.getSourceFolderFullPath());
        // 获取 source2Internal sync-flow
        SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.getSourceSyncFlowByFolderId(
                sourceInternalFolderPair.getLeft().getRootFolderId());
        // 创建 content folder
        RootFolderEntity contentFolderEntity = this.rootFolderService.createContentFolder(
                createSyncFlowRequest.getSourceFolderFullPath(),
                createSyncFlowRequest.getDestFolderFullPath()
        );
        // 创建 sync-flow
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                createSyncFlowRequest.getSyncFlowName(),
                sourceInternalFolderPair.getRight().getRootFolderId(),
                contentFolderEntity.getRootFolderId(),
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
        this.rootFolderEventProducer.addMonitor(contentFolderEntity);
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
        Pair<RootFolderEntity, RootFolderEntity> sourceInternalFolderEntityPair =
                this.rootFolderService.getFolderPairByPath(sourceFolderFullPath);
        RootFolderEntity destFolderEntity = this.rootFolderService.getByFolderFullPath(destFolderFullPath);
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
        RootFolderEntity internalFolderEntity = sourceInternalFolderEntityPair.getRight();
        List<SyncFlowEntity> internalSyncFlowList =
                this.syncFlowService.getInternalSyncFlowByFolderId(internalFolderEntity.getRootFolderId());
        for (SyncFlowEntity internalSyncFlowEntity : internalSyncFlowList) {
            if (internalSyncFlowEntity.getDestFolderId().equals(destFolderEntity.getRootFolderId())) {
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
        if (ObjectUtils.anyNull(createSyncFlowRequest.getFlattenFolder())) {
            throw new SyncDuoException("isSyncFlowRequestValid failed. flattenFolder is null");
        }
        if (StringUtils.isBlank(createSyncFlowRequest.getSyncFlowName())) {
            throw new SyncDuoException("isSyncFlowRequestValid failed. syncFlowName is null");
        }
        String sourceFolderFullPath = createSyncFlowRequest.getSourceFolderFullPath();
        String destParentFolderFullPath = createSyncFlowRequest.getDestParentFolderFullPath();
        if (StringUtils.isAnyBlank(
                sourceFolderFullPath,
                createSyncFlowRequest.getDestParentFolderFullPath())) {
            throw new SyncDuoException("isSyncFlowRequestValid failed. " +
                    "sourceFolderFullPath or destParentFolderFullPath is null");
        }
        // 检查 sourceFolderPath 路径是否正确
        Path sourceFolder = FileOperationUtils.isFolderPathValid(sourceFolderFullPath);
        // 检查 destParentFolderFullPath 路径是否正确
        FileOperationUtils.isFolderPathValid(destParentFolderFullPath);
        // 如果 destFolderFullPath 没有填写, 则使用 sourceFolderName
        String destFolderName = createSyncFlowRequest.getDestFolderName();
        if (StringUtils.isBlank(destFolderName)) {
            destFolderName = sourceFolder.getFileName().toString();
        }
        String destFolderFullPath = destParentFolderFullPath + FileOperationUtils.getPathSeparator() + destFolderName;
        if (FileOperationUtils.isFolderPathExist(destFolderFullPath)) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. destFolderFullPath already exist");
        }
        createSyncFlowRequest.setDestFolderFullPath(destFolderFullPath);
    }

    private SyncFlowInfo toSyncFlowInfo(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new SyncDuoException("syncFlowEntity is null");
        }
        // get folder info
        // input always is internal->content sync flow
        SyncFlowEntity sourceToInternalSyncFlow =
                this.syncFlowService.getSourceSyncFlowByInternalFolderId(syncFlowEntity.getSourceFolderId());
        RootFolderEntity sourceFolderEntity =
                this.rootFolderService.getByFolderId(sourceToInternalSyncFlow.getSourceFolderId());
        RootFolderEntity destFolderEntity = this.rootFolderService.getByFolderId(syncFlowEntity.getDestFolderId());
        // get sync setting
        SyncSettingEntity syncSettingEntity = this.syncSettingService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        SyncSettingEnum syncSettingEnum = SyncSettingEnum.getByCode(syncSettingEntity.getFlattenFolder());
        if (ObjectUtils.isEmpty(syncSettingEnum)) {
            throw new SyncDuoException("syncFlowEntityToSyncFlowInfo failed. " +
                    "syncFlowEntity is %s".formatted(syncFlowEntity));
        }
        // get dest folder info
        List<Long> folderInfo = FileOperationUtils.getFolderInfo(destFolderEntity.getRootFolderFullPath());
        // build result
        SyncFlowInfo result = SyncFlowInfo.builder()
                .syncFlowName(syncFlowEntity.getSyncFlowName())
                .syncFlowId(syncFlowEntity.getSyncFlowId().toString())
                .sourceFolderPath(sourceFolderEntity.getRootFolderFullPath())
                .destFolderPath(destFolderEntity.getRootFolderFullPath())
                .syncSettings(syncSettingEnum.name())
                .ignorePatten(syncSettingEntity.getFilterCriteria())
                .syncStatus(syncFlowEntity.getSyncStatus())
                .build();
        if (ObjectUtils.isNotEmpty(syncFlowEntity.getLastSyncTime())) {
           result.setLastSyncTimeStamp(SIMPLE_DATE_FORMAT.format(syncFlowEntity.getLastSyncTime()));
        }
        result.setFolderStats(
                folderInfo.get(0).toString(),
                folderInfo.get(1).toString(),
                getSizeInGB(folderInfo.get(2)));

        return result;
    }

    // Method to calculate size in GB and return only integer part
    private static String getSizeInGB(long sizeInBytes) {
        // Convert bytes to the appropriate unit based on size
        String formattedSize;
        if (sizeInBytes >= GB) {
            // Convert to GB if bytes are greater than or equal to 1 GB
            formattedSize = DECIMAL_FORMAT.format(sizeInBytes / GB) + " GB";
        } else if (sizeInBytes >= MB) {
            // Convert to MB if bytes are greater than or equal to 1 MB but less than 1 GB
            formattedSize = DECIMAL_FORMAT.format(sizeInBytes / MB) + " MB";
        } else if (sizeInBytes >= KB) {
            // Convert to KB if bytes are greater than or equal to 1 KB but less than 1 GB
            formattedSize = DECIMAL_FORMAT.format(sizeInBytes / KB) + " KB";
        } else {
            // If bytes are less than 1 MB, keep as bytes
            formattedSize = DECIMAL_FORMAT.format((double) sizeInBytes) + " Bytes";
        }
        return formattedSize;
    }
}
