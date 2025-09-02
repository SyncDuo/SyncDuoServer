package com.syncduo.server.controller;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.global.FolderStats;
import com.syncduo.server.model.api.syncflow.*;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.model.api.syncflow.UpdateFilterCriteriaRequest;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.db.impl.*;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/sync-flow")
@Slf4j
@CrossOrigin
public class SyncFlowController {

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final SyncFlowService syncFlowService;

    private final FolderWatcher folderWatcher;

    private final RcloneFacadeService rcloneFacadeService;

    @Value("${syncduo.server.system.syncflowDelayDeleteSec}")
    private long delayDeleteSec;

    @Autowired
    public SyncFlowController(
            DebounceService debounceService,
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            RcloneFacadeService rcloneFacadeService) {
        this.moduleDebounceService = debounceService.forModule(SyncFlowController.class.getSimpleName());
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
    }

    @PostMapping("/update-filter-criteria")
    public SyncFlowResponse updateFilterCriteria(
            @RequestBody UpdateFilterCriteriaRequest updateFilterCriteriaRequest) {
        try {
            // 参数检查
            EntityValidationUtil.isUpdateFilterCriteriaRequestValid(updateFilterCriteriaRequest);
            // 反序列化
            long syncFlowId = updateFilterCriteriaRequest.getSyncFlowIdInner();
            // pause 的 sync flow, 才能 update filter criteria
            SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowId);
            if (ObjectUtils.isEmpty(syncFlowEntity)) {
                return SyncFlowResponse.onSuccess("updateFilterCriteria success. SyncFlow is deleted");
            }
            if (!SyncFlowStatusEnum.PAUSE.name().equals(syncFlowEntity.getSyncStatus())) {
                return SyncFlowResponse.onError("updateFilterCriteria failed. sync flow status is not PAUSE");
            }
            // 更新 filter
            syncFlowEntity.setFilterCriteria(updateFilterCriteriaRequest.getFilterCriteria());
            this.syncFlowService.updateById(syncFlowEntity);
            // 修改完成
            return SyncFlowResponse.onSuccess("updateFilterCriteria success");
        } catch (SyncDuoException e) {
            return this.generateSyncFlowErrorResponse("updateFilterCriteria failed.", e);
        }
    }

    // change to pause, scan
    @PostMapping("/change-sync-flow-status")
    public SyncFlowResponse changeSyncFlowStatus(
            @RequestBody ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) {
        try {
            EntityValidationUtil.isChangeSyncFlowStatusRequestValid(changeSyncFlowStatusRequest);
            // 参数转换
            long syncFlowId = changeSyncFlowStatusRequest.getSyncFlowIdInner();
            SyncFlowStatusEnum to = changeSyncFlowStatusRequest.getSyncFlowStatusEnum();
            // 获取需要更改状态的 sync flow
            SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowId);
            if (ObjectUtils.isEmpty(syncFlowEntity)) {
                return SyncFlowResponse.onSuccess("changeSyncFlowStatus success. sync flow is deleted");
            }
            // 更改状态
            if (this.changeSyncFlowStatusInner(syncFlowEntity, to)) {
                return SyncFlowResponse.onSuccess("changeSyncFlowStatus success");
            }
            return SyncFlowResponse.onError("changeSyncFlowStatus failed. " +
                    "sync flow status transition invalid. " +
                    "from %s to %s".formatted(syncFlowEntity.getSyncStatus(), to));
        } catch (SyncDuoException e) {
            return this.generateSyncFlowErrorResponse("changeSyncFlowStatus failed.", e);
        }
    }

    @PostMapping("/change-all-sync-flow-status")
    public SyncFlowResponse changeAllSyncFlowStatus(
            @RequestBody ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) {
        try {
            // 参数检查
            EntityValidationUtil.isChangeSyncFlowStatusRequestValid(changeSyncFlowStatusRequest);
            // 获取全部 syncflow
            List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
            if (CollectionUtils.isEmpty(allSyncFlow)) {
                return SyncFlowResponse.onSuccess("changeAllSyncFlowStatus success. " +
                        "allSyncFlow is deleted");
            }
            // 遍历 syncflow 检查
            SyncFlowStatusEnum to = changeSyncFlowStatusRequest.getSyncFlowStatusEnum();
            for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                try {
                    this.changeSyncFlowStatusInner(syncFlowEntity, to);
                } catch (SyncDuoException e) {
                    log.error("changeAllSyncFlowStatus failed.", e);
                }
            }
            return SyncFlowResponse.onSuccess("changeAllSyncFlowStatus success.");
        } catch (SyncDuoException e) {
            return this.generateSyncFlowErrorResponse("changeAllSyncFlowStatus failed.", e);
        }
    }

    private boolean changeSyncFlowStatusInner(
            SyncFlowEntity syncFlowEntity,
            SyncFlowStatusEnum to) throws SyncDuoException {
        SyncFlowStatusEnum from = SyncFlowStatusEnum.valueOf(syncFlowEntity.getSyncStatus());
        if (!SyncFlowStatusEnum.isTransitionValid(from, to)) {
            return false;
        }
        switch (to) {
            // sync 和 running 忽略, 因为这两个状态不应从前端传回
            case SYNC, RUNNING: {
                break;
            }
            case PAUSE: {
                // 更新状态
                this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.PAUSE);
                break;
            }
            case RESCAN, RESUME: {
                // check
                boolean isSync = this.rcloneFacadeService.oneWayCheck(syncFlowEntity);
                if (isSync) {
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
                } else {
                    // sync copy
                    this.rcloneFacadeService.syncCopy(syncFlowEntity);
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.RUNNING);
                }
                break;
            }
        }
        return true;
    }

    @PostMapping("/add-sync-flow")
    public SyncFlowResponse addSyncFlow(@RequestBody CreateSyncFlowRequest createSyncFlowRequest) {
        try {
            // 参数检查
            this.isCreateSyncFlowRequestValid(createSyncFlowRequest);
            // 创建 syncflow
            SyncFlowEntity syncFlowEntity = this.syncFlowService.createSyncFlow(createSyncFlowRequest);
            // 创建 watcher
            this.folderWatcher.addWatcher(syncFlowEntity.getSourceFolderPath());
            // 初始化, 触发一次 sync copy
            this.rcloneFacadeService.syncCopy(syncFlowEntity);
            // 返回 syncflow info
            SyncFlowInfo result = new SyncFlowInfo(syncFlowEntity);
            return SyncFlowResponse.onSuccess("创建 syncflow 成功", Collections.singletonList(result));
        } catch (SyncDuoException e) {
            return this.generateSyncFlowErrorResponse("addSyncFlow failed.", e);
        }
    }

    @PostMapping("/delete-sync-flow")
    public SyncFlowResponse deleteSyncFlow(@RequestBody DeleteSyncFlowRequest deleteSyncFlowRequest) {
        try {
            EntityValidationUtil.isDeleteSyncFlowRequestValid(deleteSyncFlowRequest);
            // search
            SyncFlowEntity dbResult = this.syncFlowService.getBySyncFlowId(
                    deleteSyncFlowRequest.getInnerSyncFlowId()
            );
            if (ObjectUtils.isEmpty(dbResult)) {
                return SyncFlowResponse.onSuccess("删除 syncFlow 成功. syncflow 不存在");
            }
            // pause
            this.syncFlowService.updateSyncFlowStatus(
                    dbResult,
                    SyncFlowStatusEnum.PAUSE
            );
            // delay and delete
            this.moduleDebounceService.schedule(
                    () -> {
                        try {
                            this.syncFlowService.deleteSyncFlow(dbResult);
                        } catch (SyncDuoException e) {
                            log.error("deleteSyncFlow failed.", e);
                        }
                    },
                    delayDeleteSec
            );
            return SyncFlowResponse.onSuccess("deleteSyncFlow success.");
        } catch (SyncDuoException e) {
            return this.generateSyncFlowErrorResponse("deleteSyncFlow failed.", e);
        }
    }

    @GetMapping("/get-sync-flow")
    public SyncFlowResponse getSyncFlow() {
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (ObjectUtils.isEmpty(allSyncFlow)) {
            return SyncFlowResponse.onSuccess("no sync flow found");
        }
        List<SyncFlowInfo> result = new ArrayList<>(allSyncFlow.size());
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            try {
                SyncFlowInfo syncFlowInfo = this.getSyncFlowInfo(syncFlowEntity);
                result.add(syncFlowInfo);
            } catch (SyncDuoException e) {
                log.warn("getSyncFlowInfo failed.", e);
            }
        }
        return SyncFlowResponse.onSuccess("getSyncFlow success.", result);
    }

    private void isCreateSyncFlowRequestValid(
            CreateSyncFlowRequest createSyncFlowRequest) throws SyncDuoException {
        EntityValidationUtil.isCreateSyncFlowRequestValid(createSyncFlowRequest);
        // 检查 source folder 是否存在
        if (!this.rcloneFacadeService.isSourceFolderExist(createSyncFlowRequest.getSourceFolderFullPath())) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "sourceFolderPath is not exist. " +
                    "createSyncFlowRequest is %s.".formatted(createSyncFlowRequest));
        }
    }

    private SyncFlowInfo getSyncFlowInfo(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 本地获取 folderStat
        List<Long> folderInfo = FilesystemUtil.getFolderInfo(syncFlowEntity.getDestFolderPath());
        FolderStats folderStats = new FolderStats(folderInfo.get(0), folderInfo.get(1), folderInfo.get(2));
        return new SyncFlowInfo(syncFlowEntity, folderStats);
    }

    private SyncFlowResponse generateSyncFlowErrorResponse(String errorMessage, SyncDuoException e) {
        SyncFlowResponse syncFlowResponse = SyncFlowResponse.onError(errorMessage + e.getMessage());
        log.warn(errorMessage, e);
        return syncFlowResponse;
    }
}
