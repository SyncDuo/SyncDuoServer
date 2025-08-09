package com.syncduo.server.controller;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.snapshots.SnapshotInfo;
import com.syncduo.server.model.api.snapshots.SnapshotsResponse;
import com.syncduo.server.model.api.snapshots.SyncFlowSnapshotsInfo;
import com.syncduo.server.model.api.syncflow.ManualBackupRequest;
import com.syncduo.server.model.api.syncflow.SyncFlowResponse;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.service.restic.ResticFacadeService;
import com.syncduo.server.util.EntityValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

@RestController
@RequestMapping("/snapshots")
@Slf4j
@CrossOrigin
public class SnapshotsController {

    private final SyncFlowService syncFlowService;

    private final BackupJobService backupJobService;
    private final ResticFacadeService resticFacadeService;

    @Autowired
    public SnapshotsController(
            SyncFlowService syncFlowService,
            BackupJobService backupJobService,
            ResticFacadeService resticFacadeService) {
        this.syncFlowService = syncFlowService;
        this.backupJobService = backupJobService;
        this.resticFacadeService = resticFacadeService;
    }

    @PostMapping("/backup")
    public SnapshotsResponse backup(@RequestBody ManualBackupRequest manualBackupRequest) {
        try {
            EntityValidationUtil.isManualBackupRequestValid(manualBackupRequest);
            // 查询 syncflow
            SyncFlowEntity syncFlowEntity =
                    this.syncFlowService.getBySyncFlowId(manualBackupRequest.getInnerSyncFlowId());
            if (ObjectUtils.isEmpty(syncFlowEntity)) {
                return SnapshotsResponse.onError("backup failed. SyncFlow is deleted");
            }
            // backup
            this.resticFacadeService.manualBackup(syncFlowEntity);
            return SnapshotsResponse.onSuccess("backup success");
        } catch (SyncDuoException e) {
            return SnapshotsResponse.onError("backup failed. ex is %s".formatted(e.getMessage()));
        }
    }

    @GetMapping("/get-snapshots")
    public SnapshotsResponse getSnapshots(@Param("syncFlowId") String syncFlowId) {
        try {
            if (StringUtils.isBlank(syncFlowId)) {
                // 获取全部 snapshots
                List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
                if (CollectionUtils.isEmpty(allSyncFlow)) {
                    return SnapshotsResponse.onSuccess("没有可用的 syncflow");
                }
                List<SyncFlowSnapshotsInfo> result = new ArrayList<>();
                for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                    SyncFlowSnapshotsInfo tmp = this.getSyncFlowSnapshotsInfo(syncFlowEntity);
                    if (ObjectUtils.isEmpty(tmp)) {
                        continue;
                    }
                    result.add(tmp);
                }
                return SnapshotsResponse.onSuccess("成功", result);
            } else {
                // 参数检查
                long syncFlowIdLong = Long.parseLong(syncFlowId);
                // 获取对应的 snapshots
                SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowIdLong);
                if (ObjectUtils.isEmpty(syncFlowEntity)) {
                    return SnapshotsResponse.onSuccess("syncflow not found");
                }
                SyncFlowSnapshotsInfo result = this.getSyncFlowSnapshotsInfo(syncFlowEntity);
                return SnapshotsResponse.onSuccess("成功", Collections.singletonList(result));
            }
        } catch (Exception e) {
            return SnapshotsResponse.onError("获取 snapshots 失败. ex 是" + e.getMessage());
        }
    }

    private SyncFlowSnapshotsInfo getSyncFlowSnapshotsInfo(SyncFlowEntity syncFlowEntity) {
        List<BackupJobEntity> backupJobEntityList =
                this.backupJobService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (CollectionUtils.isEmpty(backupJobEntityList)) {
            return getSyncFlowSnapshotsInfo(syncFlowEntity, Collections.emptyList());
        }
        // 排序, 时间倒排
        backupJobEntityList.sort(Comparator.comparing(
                (backupJobEntity) -> (
                        backupJobEntity.getFinishedAt() != null) ?
                        backupJobEntity.getFinishedAt() :
                        backupJobEntity.getLastUpdatedTime(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        // snapshot 结果集
        List<SnapshotInfo> snapshotInfos = new ArrayList<>();
        // snapshot 格式化使用的变量
        BigDecimal mbDivider = new BigDecimal(1024 * 1024);
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        for (BackupJobEntity backupJobEntity : backupJobEntityList) {
            SnapshotInfo snapshotInfo = new SnapshotInfo();
            // fall back 处理, 如果两值为空, 使用 audit field
            if (ObjectUtils.anyNull(backupJobEntity.getStartedAt(), backupJobEntity.getFinishedAt())) {
                snapshotInfo.setStartedAt(backupJobEntity.getCreatedTime().toString());
                snapshotInfo.setFinishedAt(backupJobEntity.getLastUpdatedTime().toString());
            } else {
                snapshotInfo.setStartedAt(backupJobEntity.getStartedAt().toString());
                snapshotInfo.setFinishedAt(backupJobEntity.getFinishedAt().toString());
            }
            // 防止空指针
            if (ObjectUtils.isNotEmpty(backupJobEntity.getSnapshotId())) {
                snapshotInfo.setSnapshotId(backupJobEntity.getSnapshotId());
            }
            // 格式化 size
            BigInteger backupBytes = backupJobEntity.getBackupBytes();
            if (ObjectUtils.isNotEmpty(backupBytes)) {
                BigDecimal backupMb = new BigDecimal(backupBytes).divide(mbDivider, 2, RoundingMode.HALF_UP);
                snapshotInfo.setSnapshotSize(decimalFormat.format(backupMb));
            }
            // 防止空指针
            if (ObjectUtils.isNotEmpty(backupJobEntity.getBackupFiles())) {
                snapshotInfo.setBackupFiles(backupJobEntity.getBackupFiles().toString());
            }
            snapshotInfo.setBackupJobStatus(backupJobEntity.getBackupJobStatus());
            snapshotInfo.setBackupErrorMessage(backupJobEntity.getErrorMessage());
            // 添加到结果集
            snapshotInfos.add(snapshotInfo);
        }
        // 处理 syncflow entity
        return getSyncFlowSnapshotsInfo(syncFlowEntity, snapshotInfos);
    }

    private static SyncFlowSnapshotsInfo getSyncFlowSnapshotsInfo(
            SyncFlowEntity syncFlowEntity,
            List<SnapshotInfo> snapshotInfos) {
        SyncFlowSnapshotsInfo syncFlowSnapshotsInfo = new SyncFlowSnapshotsInfo();
        syncFlowSnapshotsInfo.setSyncFlowId(syncFlowEntity.getSyncFlowId().toString());
        syncFlowSnapshotsInfo.setSyncFlowName(syncFlowEntity.getSyncFlowName());
        syncFlowSnapshotsInfo.setSourceFolderPath(syncFlowEntity.getSourceFolderPath());
        syncFlowSnapshotsInfo.setDestFolderPath(syncFlowEntity.getDestFolderPath());
        if (CollectionUtils.isNotEmpty(snapshotInfos)) {
            syncFlowSnapshotsInfo.setSnapshotInfoList(snapshotInfos);
        }
        return syncFlowSnapshotsInfo;
    }
}
