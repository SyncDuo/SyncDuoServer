package com.syncduo.server.model.api.snapshots;

import com.syncduo.server.model.entity.BackupJobEntity;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;

@Data
public class SnapshotInfo {

    private String backupJobId;

    private String startedAt; // timestamp

    private String finishedAt; // timestamp

    private String snapshotId;

    private String snapshotSize; // MB

    private String backupFiles;

    private String backupJobStatus;

    private String backupErrorMessage;

    public static SnapshotInfo getFromBackupJobEntity(BackupJobEntity backupJobEntity) {
        if (ObjectUtils.isEmpty(backupJobEntity)) {
            return null;
        }
        SnapshotInfo snapshotInfo = new SnapshotInfo();
        snapshotInfo.setBackupJobId(backupJobEntity.getBackupJobId().toString());
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
            // BigInteger 格式化使用的变量, bytes -> MB
            BigDecimal mbDivider = new BigDecimal(1024 * 1024);
            DecimalFormat decimalFormat = new DecimalFormat("0.00");
            BigDecimal backupMb = new BigDecimal(backupBytes).divide(mbDivider, 2, RoundingMode.HALF_UP);
            snapshotInfo.setSnapshotSize(decimalFormat.format(backupMb));
        }
        // 防止空指针
        if (ObjectUtils.isNotEmpty(backupJobEntity.getBackupFiles())) {
            snapshotInfo.setBackupFiles(backupJobEntity.getBackupFiles().toString());
        }
        snapshotInfo.setBackupJobStatus(backupJobEntity.getBackupJobStatus());
        snapshotInfo.setBackupErrorMessage(backupJobEntity.getErrorMessage());
        return snapshotInfo;
    }
}
