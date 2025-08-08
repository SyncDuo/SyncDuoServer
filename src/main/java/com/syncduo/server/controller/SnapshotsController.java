package com.syncduo.server.controller;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.model.api.snapshots.SnapshotsInfo;
import com.syncduo.server.model.api.snapshots.SnapshotsResponse;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
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
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.*;

@RestController
@RequestMapping("/snapshots")
@Slf4j
@CrossOrigin
public class SnapshotsController {

    private final FolderWatcher folderWatcher;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    private final BackupJobService backupJobService;

    @Autowired
    public SnapshotsController(
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            RcloneFacadeService rcloneFacadeService,
            BackupJobService backupJobService) {
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
        this.backupJobService = backupJobService;
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
                Map<String, List<SnapshotsInfo>> result = new HashMap<>();
                for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                    result.put(syncFlowEntity.getSyncFlowId().toString(), this.getSnapshotsInfo(syncFlowEntity));
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
                HashMap<String, List<SnapshotsInfo>> result = new HashMap<>();
                result.put(syncFlowEntity.getSyncFlowId().toString(), this.getSnapshotsInfo(syncFlowEntity));
                return SnapshotsResponse.onSuccess("成功", result);
            }
        } catch (Exception e) {
            return SnapshotsResponse.onError("获取 snapshots 失败. ex 是" + e.getMessage());
        }
    }

    private List<SnapshotsInfo> getSnapshotsInfo(SyncFlowEntity syncFlowEntity) {
        List<BackupJobEntity> backupJobEntityList =
                this.backupJobService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (CollectionUtils.isEmpty(backupJobEntityList)) {
            return Collections.emptyList();
        }
        // 排序, 时间倒排
        backupJobEntityList.sort(Comparator.comparing(
                BackupJobEntity::getFinishedAt,
                        Comparator.nullsLast(Timestamp::compareTo))
                .reversed());
        // 结果集
        List<SnapshotsInfo> result = new ArrayList<>();
        // 格式化使用的变量
        BigDecimal mbDivider = new BigDecimal(1024 * 1024);
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        for (BackupJobEntity backupJobEntity : backupJobEntityList) {
            SnapshotsInfo snapshotsInfo = new SnapshotsInfo();
            snapshotsInfo.setDestFolderPath(syncFlowEntity.getDestFolderPath());
            // fall back 处理, 如果两值为空, 使用 audit field
            if (ObjectUtils.anyNull(backupJobEntity.getStartedAt(), backupJobEntity.getFinishedAt())) {
                snapshotsInfo.setStartedAt(backupJobEntity.getCreatedTime().toString());
                snapshotsInfo.setFinishedAt(backupJobEntity.getLastUpdatedTime().toString());
            } else {
                snapshotsInfo.setStartedAt(backupJobEntity.getStartedAt().toString());
                snapshotsInfo.setFinishedAt(backupJobEntity.getFinishedAt().toString());
            }
            // 防止空指针
            if (ObjectUtils.isNotEmpty(backupJobEntity.getSnapshotId())) {
                snapshotsInfo.setSnapshotId(backupJobEntity.getSnapshotId());
            }
            // 格式化 size
            BigInteger backupBytes = backupJobEntity.getBackupBytes();
            if (ObjectUtils.isNotEmpty(backupBytes)) {
                BigDecimal backupMb = new BigDecimal(backupBytes).divide(mbDivider, 2, RoundingMode.HALF_UP);
                snapshotsInfo.setSnapshotSize(decimalFormat.format(backupMb));
            }
            // 防止空指针
            if (ObjectUtils.isNotEmpty(backupJobEntity.getBackupFiles())) {
                snapshotsInfo.setBackupFiles(backupJobEntity.getBackupFiles().toString());
            }
            snapshotsInfo.setBackupJobStatus(backupJobEntity.getBackupJobStatus());
            snapshotsInfo.setBackupErrorMessage(backupJobEntity.getErrorMessage());
            // 添加到结果集
            result.add(snapshotsInfo);
        }
        return result;
    }
}
