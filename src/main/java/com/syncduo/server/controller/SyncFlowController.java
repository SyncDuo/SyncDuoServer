package com.syncduo.server.controller;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.*;
import com.syncduo.server.model.api.global.FolderStats;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.model.api.syncflow.*;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/sync-flow")
@Slf4j
@CrossOrigin(originPatterns = "*")
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
    public SyncDuoHttpResponse<Void> updateFilterCriteria(
            @RequestBody UpdateFilterCriteriaRequest updateFilterCriteriaRequest) {
        // 参数检查
        EntityValidationUtil.isUpdateFilterCriteriaRequestValid(updateFilterCriteriaRequest);
        // 反序列化
        long syncFlowId = updateFilterCriteriaRequest.getSyncFlowIdInner();
        // pause 的 sync flow, 才能 update filter criteria
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            return SyncDuoHttpResponse.success(null, "SyncFlow is deleted");
        }
        if (!SyncFlowStatusEnum.PAUSE.name().equals(syncFlowEntity.getSyncStatus())) {
            throw new ValidationException("sync flow status is not PAUSE");
        }
        // 更新 filter
        syncFlowEntity.setFilterCriteria(updateFilterCriteriaRequest.getFilterCriteria());
        this.syncFlowService.updateById(syncFlowEntity);
        // 修改完成
        return SyncDuoHttpResponse.success();
    }

    // change to pause, scan
    @PostMapping("/change-sync-flow-status")
    public SyncDuoHttpResponse<Void> changeSyncFlowStatus(
            @RequestBody ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) {
        EntityValidationUtil.isChangeSyncFlowStatusRequestValid(changeSyncFlowStatusRequest);
        // 参数转换
        long syncFlowId = changeSyncFlowStatusRequest.getSyncFlowIdInner();
        SyncFlowStatusEnum to = changeSyncFlowStatusRequest.getSyncFlowStatusEnum();
        // 获取需要更改状态的 sync flow
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            return SyncDuoHttpResponse.success(null, "sync flow is deleted");
        }
        SyncFlowStatusEnum from = SyncFlowStatusEnum.valueOf(syncFlowEntity.getSyncStatus());
        if (!SyncFlowStatusEnum.isTransitionValid(from, to)) {
            throw new ValidationException("change status from %s to %s is not valid".formatted(from, to));
        }
        // 更改状态
        this.changeSyncFlowStatusInner(syncFlowEntity, to);
        return SyncDuoHttpResponse.success();
    }

    @PostMapping("/change-all-sync-flow-status")
    public SyncDuoHttpResponse<Void> changeAllSyncFlowStatus(
            @RequestBody ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) {
        // 参数检查
        EntityValidationUtil.isChangeSyncFlowStatusRequestValid(changeSyncFlowStatusRequest);
        // 获取全部 syncflow
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return SyncDuoHttpResponse.success(null, "allSyncFlow is deleted");
        }
        // 遍历 syncflow 检查
        SyncFlowStatusEnum to = changeSyncFlowStatusRequest.getSyncFlowStatusEnum();
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            try {
                SyncFlowStatusEnum from = SyncFlowStatusEnum.valueOf(syncFlowEntity.getSyncStatus());
                if (!SyncFlowStatusEnum.isTransitionValid(from, to)) {
                    continue;
                }
                this.changeSyncFlowStatusInner(syncFlowEntity, to);
            } catch (Exception e) {
                log.error("changeAllSyncFlowStatus failed.",
                        new BusinessException("changeAllSyncFlowStatus failed", e));
            }
        }
        return SyncDuoHttpResponse.success();
    }

    private void changeSyncFlowStatusInner(SyncFlowEntity syncFlowEntity, SyncFlowStatusEnum to)
    throws ValidationException, DbException {
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
            case FAILED, RESCAN, RESUME: {
                // check
                boolean isSync = this.rcloneFacadeService.oneWayCheck(syncFlowEntity);
                if (isSync) {
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
                } else {
                    // sync copy
                    this.rcloneFacadeService.syncCopy(syncFlowEntity);
                }
                break;
            }
        }
    }

    @PostMapping("/add-sync-flow")
    public SyncDuoHttpResponse<SyncFlowInfo> addSyncFlow(@RequestBody CreateSyncFlowRequest createSyncFlowRequest) {
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
        return SyncDuoHttpResponse.success(result);
    }

    @PostMapping("/delete-sync-flow")
    public SyncDuoHttpResponse<Void> deleteSyncFlow(@RequestBody DeleteSyncFlowRequest deleteSyncFlowRequest) {
        EntityValidationUtil.isDeleteSyncFlowRequestValid(deleteSyncFlowRequest);
        // search
        SyncFlowEntity dbResult = this.syncFlowService.getBySyncFlowId(
                deleteSyncFlowRequest.getInnerSyncFlowId()
        );
        if (ObjectUtils.isEmpty(dbResult)) {
            return SyncDuoHttpResponse.success(null, "删除 syncFlow 成功. syncflow 不存在");
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
                    } catch (Exception e) {
                        log.error("deleteSyncFlow failed.",
                                new BusinessException("deleteSyncFlow failed", e));
                    }
                },
                delayDeleteSec
        );
        return SyncDuoHttpResponse.success();
    }

    @GetMapping("/get-sync-flow-info")
    public SyncDuoHttpResponse<SyncFlowInfo> getSyncFlowInfo(
            @RequestParam("syncFlowIdString") String syncFlowIdString) {
        if (StringUtils.isBlank(syncFlowIdString)) {
            throw new ValidationException("syncFlowIdString is empty");
        }
        long syncFlowId;
        try {
            syncFlowId = Long.parseLong(syncFlowIdString);
        } catch (NumberFormatException e) {
            throw new ValidationException("syncFlowIdString is invalid.");
        }
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            return SyncDuoHttpResponse.success(null, "syncFlow is deleted");
        }
        return SyncDuoHttpResponse.success(this.getSyncFlowInfo(syncFlowEntity));
    }

    @GetMapping("/get-all-sync-flow-info")
    public SyncDuoHttpResponse<List<SyncFlowInfo>> getAllSyncFlowInfo() {
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (ObjectUtils.isEmpty(allSyncFlow)) {
            return SyncDuoHttpResponse.success(null, "no sync flow found");
        }
        List<SyncFlowInfo> result = new ArrayList<>(allSyncFlow.size());
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            try {
                SyncFlowInfo syncFlowInfo = this.getSyncFlowInfo(syncFlowEntity);
                result.add(syncFlowInfo);
            } catch (Exception e) {
                log.warn("getSyncFlowInfo failed.",
                        new BusinessException("getSyncFlowInfo failed", e));
            }
        }
        return SyncDuoHttpResponse.success(result);
    }

    private void isCreateSyncFlowRequestValid(
            CreateSyncFlowRequest createSyncFlowRequest) throws ValidationException {
        EntityValidationUtil.isCreateSyncFlowRequestValid(createSyncFlowRequest);
        // 检查 source folder 是否存在
        if (!this.rcloneFacadeService.isSourceFolderExist(createSyncFlowRequest.getSourceFolderFullPath())) {
            throw new ValidationException("isCreateSyncFlowRequestValid failed. " +
                    "sourceFolderPath is not exist. " +
                    "createSyncFlowRequest is %s.".formatted(createSyncFlowRequest));
        }
    }

    private SyncFlowInfo getSyncFlowInfo(SyncFlowEntity syncFlowEntity)
            throws ValidationException, ResourceNotFoundException, FileOperationException {
        // 本地获取 folderStat
        List<Long> folderInfo = FilesystemUtil.getFolderInfo(syncFlowEntity.getDestFolderPath());
        FolderStats folderStats = new FolderStats(folderInfo.get(0), folderInfo.get(1), folderInfo.get(2));
        return new SyncFlowInfo(syncFlowEntity, folderStats);
    }
}
