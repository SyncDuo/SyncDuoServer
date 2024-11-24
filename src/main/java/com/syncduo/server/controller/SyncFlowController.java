package com.syncduo.server.controller;

import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.producer.RootFolderEventProducer;
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

    private final RootFolderEventProducer rootFolderEventProducer;

    @Autowired
    public SyncFlowController(
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            AdvancedFileOpService fileOpService,
            RootFolderEventProducer rootFolderEventProducer) {
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.fileOpService = fileOpService;
        this.rootFolderEventProducer = rootFolderEventProducer;
    }

    @PostMapping("/source-2-content")
    public SyncFlowResponse addSource2ContentSyncFlow(@RequestBody SyncFlowRequest syncFlowRequest) {

        return SyncFlowResponse.onSuccess("创建关联关系成功");
    }

    private void firstTimeCreateSourceFolder(SyncFlowRequest syncFlowRequest) throws SyncDuoException {
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
        // 执行 init scan 任务
        this.fileOpService.initialScan(sourceFolderEntity);
        // 执行 addWatcher
        this.rootFolderEventProducer.addWatcher(sourceFolderEntity);
        this.rootFolderEventProducer.addWatcher(contentFolderEntity);
    }

    private RootFolderEntity createContentFolder(SyncFlowRequest syncFlowRequest) throws SyncDuoException {
        return this.rootFolderService.createContentFolder(
                syncFlowRequest.getSourceFolderFullPath(),
                syncFlowRequest.getDestFolderFullPath()
        );
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
