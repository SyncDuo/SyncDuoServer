package com.syncduo.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.mq.producer.RootFolderEventProducer;
import com.syncduo.server.service.impl.AdvancedFileOpService;
import com.syncduo.server.service.impl.RootFolderService;
import com.syncduo.server.service.impl.SyncFlowService;
import com.syncduo.server.service.impl.SyncSettingService;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController("/sync-flow")
@Slf4j
public class SyncFlowController {
    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final AdvancedFileOpService fileOpService;

    private final SyncSettingService syncSettingService;

    private final RootFolderEventProducer rootFolderEventProducer;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> LIST_STRING_TYPE_REFERENCE = new TypeReference<>(){};

    @Autowired
    public SyncFlowController(
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            AdvancedFileOpService fileOpService,
            SyncSettingService syncSettingService,
            RootFolderEventProducer rootFolderEventProducer) {
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileOpService = fileOpService;
        this.syncSettingService = syncSettingService;
        this.rootFolderEventProducer = rootFolderEventProducer;
    }

    @PostMapping("/source-2-content")
    public SyncFlowResponse addSource2ContentSyncFlow(@RequestBody SyncFlowRequest syncFlowRequest) {
        // 参数检查
        try {
            this.isSyncFlowRequestValid(syncFlowRequest);
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("参数检查不合法" + e.getMessage());
        }
        // 检查过滤条件
        List<String> filters = new ArrayList<>();
        if (StringUtils.isNoneBlank(syncFlowRequest.getFilterCriteria())) {
            try {
                filters = OBJECT_MAPPER.readValue(syncFlowRequest.getFilterCriteria(), LIST_STRING_TYPE_REFERENCE);
            } catch (JsonProcessingException e) {
                return SyncFlowResponse.onError("无法反序列化过滤条件 " + e.getMessage());
            }
        }

        // 判断 source folder 和 dest folder 是否相同
        if (syncFlowRequest.getSourceFolderFullPath().equals(syncFlowRequest.getDestFolderFullPath())) {
            return SyncFlowResponse.onError("source folder 和 dest folder 路径相同. %s".formatted(syncFlowRequest));
        }
        // 判断 sync-flow 是否已经存在
        try {
            int resultCode = this.isSyncFlowExist(syncFlowRequest);
            switch (resultCode) {
                case 0 -> {
                    firstTimeCreateSourceFolder(syncFlowRequest, filters);
                    return SyncFlowResponse.onSuccess("sync-flow 创建成功");
                }
                case 1 -> {
                    this.createContentSyncFlow(syncFlowRequest, filters);
                    return SyncFlowResponse.onSuccess("sync-flow 创建成功");
                }
                case 2 -> {
                    return SyncFlowResponse.onSuccess("sync-flow 已存在");
                }
                default -> throw new SyncDuoException("不识别的 result code : " + resultCode);
            }
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError("检查 sync-flow 失败" + e.getMessage());
        }
    }

    private void firstTimeCreateSourceFolder(SyncFlowRequest syncFlowRequest, List<String> filters)
            throws SyncDuoException {
        String sourceFolderFullPath = syncFlowRequest.getSourceFolderFullPath();
        // 创建 source folder 和 internal folder
        Pair<RootFolderEntity, RootFolderEntity> sourceAndInternalFolderEntity =
                this.rootFolderService.createSourceFolder(sourceFolderFullPath);
        RootFolderEntity sourceFolderEntity = sourceAndInternalFolderEntity.getLeft();
        RootFolderEntity internalFolderEntity = sourceAndInternalFolderEntity.getRight();
        // 创建 content folder
        RootFolderEntity contentFolderEntity =
                this.rootFolderService.createContentFolder(
                        sourceFolderFullPath,
                        syncFlowRequest.getDestFolderFullPath());
        // 创建 source to internal sync flow
        SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.createSyncFlow(
                sourceFolderEntity.getRootFolderId(),
                internalFolderEntity.getRootFolderId(),
                SyncFlowTypeEnum.SOURCE_TO_INTERNAL);
        // 创建 internal to content sync flow
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                sourceFolderEntity.getRootFolderId(),
                contentFolderEntity.getRootFolderId(),
                SyncFlowTypeEnum.INTERNAL_TO_CONTENT
        );
        // 创建 sync setting
        SyncSettingEntity syncSettingEntity = this.syncSettingService.createSyncSetting(
                internal2ContentSyncFlow.getSyncFlowId(),
                filters,
                syncFlowRequest.getFlattenFolder() ? SyncSettingEnum.FLATTEN_FOLDER : SyncSettingEnum.MIRROR
        );
        // 执行 init scan 任务
        this.fileOpService.initialScan(sourceFolderEntity);
        // 执行 addWatcher
        this.rootFolderEventProducer.addWatcher(sourceFolderEntity);
        this.rootFolderEventProducer.addWatcher(contentFolderEntity);
    }

    private void createContentSyncFlow(SyncFlowRequest syncFlowRequest, List<String> filters)
            throws SyncDuoException {
        // 获取 source 和 internal folder entity
        Pair<RootFolderEntity, RootFolderEntity> sourceInternalFolderPair =
                this.rootFolderService.getFolderPairByPath(syncFlowRequest.getSourceFolderFullPath());
        // 创建 content folder
        RootFolderEntity contentFolderEntity = this.rootFolderService.createContentFolder(
                syncFlowRequest.getSourceFolderFullPath(),
                syncFlowRequest.getDestFolderFullPath()
        );
        // 创建 sync-flow
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                sourceInternalFolderPair.getRight().getRootFolderId(),
                contentFolderEntity.getRootFolderId(),
                SyncFlowTypeEnum.INTERNAL_TO_CONTENT
        );
        // 创建 sync setting
        SyncSettingEntity syncSettingEntity = this.syncSettingService.createSyncSetting(
                internal2ContentSyncFlow.getSyncFlowId(),
                filters,
                syncFlowRequest.getFlattenFolder() ? SyncSettingEnum.FLATTEN_FOLDER : SyncSettingEnum.MIRROR
        );
        // 执行 init scan 任务
        this.fileOpService.initialScan(contentFolderEntity);
        // 执行 addWatcher
        this.rootFolderEventProducer.addWatcher(contentFolderEntity);
    }


    // 0: source folder -> dest folder 不存在
    // 1: source folder -> internal folder 存在, dest folder 不存在
    // 2: source folder -> dest folder 存在
    private int isSyncFlowExist(SyncFlowRequest syncFlowRequest) throws SyncDuoException {
        // 获取 source folder full path 和 dest folder full path
        String sourceFolderFullPath = syncFlowRequest.getSourceFolderFullPath();
        String destFolderFullPath = syncFlowRequest.getDestFolderFullPath();
        // 检查 source folder, internal folder, dest folder 是否已存在
        Pair<RootFolderEntity, RootFolderEntity> sourceInternalFolderEntityPair =
                this.rootFolderService.getFolderPairByPath(sourceFolderFullPath);
        RootFolderEntity destFolderEntity = this.rootFolderService.getByFolderFullPath(destFolderFullPath);
        if (ObjectUtils.isEmpty(sourceInternalFolderEntityPair)) {
            if (ObjectUtils.isEmpty(destFolderEntity)) {
                return 0;
            } else {
                throw new SyncDuoException("source 和 internal folder 为空, 但是 dest folder 不为空");
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

    private void isSyncFlowRequestValid(SyncFlowRequest syncFlowRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowRequest)) {
            throw new SyncDuoException("SyncFlowRequest 检查失败,为空");
        }
        if (ObjectUtils.anyNull(syncFlowRequest.getConcatDestFolderPath(), syncFlowRequest.getFlattenFolder())) {
            throw new SyncDuoException("SyncFlowRequest 检查失败, concatDestFolderPath 或 flattenFolder 为空");
        }
        String sourceFolderFullPath = syncFlowRequest.getSourceFolderFullPath();
        String destFolderFullPath = syncFlowRequest.getDestFolderFullPath();
        if (StringUtils.isAnyBlank(
                sourceFolderFullPath,
                destFolderFullPath)) {
            throw new SyncDuoException(
                    "SyncFlowRequest 检查失败, sourceFolderFullPath, 或 destFolderFullPath 为空");
        }
        // 检查 sourceFolderPath 路径是否正确
        Path sourceFolder = FileOperationUtils.isFolderPathValid(syncFlowRequest.getSourceFolderFullPath());
        // 按照输入拼接 destFolderFullPath
        if (syncFlowRequest.getConcatDestFolderPath()) {
            destFolderFullPath = syncFlowRequest.getDestFolderFullPath() +
                    FileOperationUtils.getSeparator() +
                    sourceFolder.getFileName();
            syncFlowRequest.setDestFolderFullPath(destFolderFullPath);
        }
        if (FileOperationUtils.endsWithSeparator(destFolderFullPath)) {
            throw new SyncDuoException(
                    "destFolderFullPath 格式不规范, 使用分隔符结尾. %s".formatted(destFolderFullPath));
        }
    }
}
