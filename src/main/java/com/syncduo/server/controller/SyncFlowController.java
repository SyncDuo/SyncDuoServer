package com.syncduo.server.controller;

import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.producer.SourceFolderEventProducer;
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
    private RootFolderService rootFolderService;

    private SyncFlowService syncFlowService;

    private SourceFolderEventProducer sourceFolderEventProducer;

    @Autowired
    public SyncFlowController(
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            SourceFolderEventProducer sourceFolderEventProducer) {
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.sourceFolderEventProducer = sourceFolderEventProducer;
    }

    @PostMapping("/source-2-content")
    public SyncFlowResponse addSource2ContentSyncFlow(@RequestBody SyncFlowRequest syncFlowRequest) {
        try {
            this.isSyncFlowRequestValid(syncFlowRequest);
            // 创建 source folder 和 internal folder
            Pair<RootFolderEntity, RootFolderEntity> sourceAndInternalFolderEntity =
                    this.rootFolderService.createSourceFolder(syncFlowRequest.getSourceFolderFullPath());
            // 创建 content folder
            RootFolderEntity contentFolderEntity =
                    this.rootFolderService.createContentFolder(syncFlowRequest.getDestFolderFullPath());
            // 创建 source to internal sync flow
            SyncFlowEntity source2InternalSyncFlow = this.syncFlowService.createSyncFlow(
                    sourceAndInternalFolderEntity.getLeft().getFolderId(),
                    sourceAndInternalFolderEntity.getRight().getFolderId(),
                    SyncFlowTypeEnum.SOURCE_TO_INTERNAL);
            // 创建 internal to content sync flow
            SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.createSyncFlow(
                    sourceAndInternalFolderEntity.getLeft().getFolderId(),
                    contentFolderEntity.getFolderId(),
                    SyncFlowTypeEnum.INTERNAL_TO_CONTENT
            );
            // 发送 full scan, compare 任务

        } catch (SyncDuoException e) {
            return SyncFlowResponse.onError(e.getMessage());
        }

        return null;
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
