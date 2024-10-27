package com.syncduo.server.controller;

import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.producer.SourceFolderEventProducer;
import com.syncduo.server.service.impl.AdvancedFileOpService;
import com.syncduo.server.service.impl.RootFolderService;
import com.syncduo.server.service.impl.SyncFlowService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/sync-flow")
public class SyncFlowController {
    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final AdvancedFileOpService fileOpService;

    private final SourceFolderEventProducer sourceFolderEventProducer;

    @Autowired
    public SyncFlowController(
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            AdvancedFileOpService fileOpService,
            SourceFolderEventProducer sourceFolderEventProducer) {
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileOpService = fileOpService;
        this.sourceFolderEventProducer = sourceFolderEventProducer;
    }

    @PostMapping("/source-2-content")
    public SyncFlowResponse addSource2ContentSyncFlow(@RequestBody SyncFlowRequest syncFlowRequest) {
        try {
            this.isSyncFlowRequestValid(syncFlowRequest);
            // 创建 source folder 和 internal folder
            Pair<RootFolderEntity, RootFolderEntity> sourceAndInternalFolderEntity =
                    this.rootFolderService.createSourceFolder(syncFlowRequest.getSourceFolderFullPath());
            RootFolderEntity sourceFolderEntity = sourceAndInternalFolderEntity.getLeft();
            RootFolderEntity internalFolderEntity = sourceAndInternalFolderEntity.getRight();
            // 创建 content folder
            RootFolderEntity contentFolderEntity =
                    this.rootFolderService.createContentFolder(syncFlowRequest.getDestFolderFullPath());
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
            // 执行 init scan 任务
            this.fileOpService.initialScan(sourceFolderEntity);
            // 执行 addWatcher
            this.sourceFolderEventProducer.addWatcher(
                    sourceFolderEntity.getRootFolderFullPath(),
                    sourceFolderEntity.getRootFolderId());
        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError(e.getMessage());
        }
        return SyncFlowResponse.onSuccess("成功创建同步关系");
    }

    private void isSyncFlowRequestValid(SyncFlowRequest syncFlowRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowRequest)) {
            throw new SyncDuoException("SyncFlowRequest 检查失败,为空");
        }
        if (StringUtils.isAnyBlank(
                syncFlowRequest.getSourceFolderFullPath(),
                syncFlowRequest.getDestFolderFullPath())) {
            throw new SyncDuoException("SyncFlowRequest 检查失败, sourceFolderFullPath 或者 destFolderFullPath 为空");
        }
    }
}
