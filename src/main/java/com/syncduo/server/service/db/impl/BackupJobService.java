package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.BackupJobStatusEnum;
import com.syncduo.server.enums.CopyJobStatusEnum;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.BackupJobMapper;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.entity.CopyJobEntity;
import com.syncduo.server.model.rclone.core.stat.CoreStatsResponse;
import com.syncduo.server.model.rclone.job.status.JobStatusResponse;
import com.syncduo.server.model.restic.backup.Summary;
import com.syncduo.server.service.db.IBackupJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
public class BackupJobService
        extends ServiceImpl<BackupJobMapper, BackupJobEntity>
        implements IBackupJobService {

    public void addSuccessBackupJob(
            long syncFlowId,
            Summary summary) throws SyncDuoException {
        BackupJobEntity backupJobEntity = new BackupJobEntity();
        backupJobEntity.setBackupJobStatus(BackupJobStatusEnum.SUCCESS.name());
        backupJobEntity.setSyncFlowId(syncFlowId);
        backupJobEntity.setSuccessMessage(summary.toString());
        // snapshot 没有 create, 则直接记录为 success
        if (StringUtils.isEmpty(summary.getSnapshotId())) {
            // 保存
            boolean saved = this.save(backupJobEntity);
            if (!saved) {
                throw new SyncDuoException("addSuccessBackupJob failed. can't write to database.");
            }
            return;
        }
        // 设置 snapshot id
        backupJobEntity.setSnapshotId(summary.getSnapshotId());
        // 设置时间
        backupJobEntity.setStartedAt(Timestamp.from(summary.getBackupStart()));
        backupJobEntity.setFinishedAt(Timestamp.from(summary.getBackupEnd()));
        // 设置 stats
        backupJobEntity.setBackupFiles(summary.getTotalFilesProcessed());
        backupJobEntity.setBackupBytes(summary.getTotalBytesProcessed());
        // 保存
        boolean saved = this.save(backupJobEntity);
        if (!saved) {
            throw new SyncDuoException("addSuccessBackupJob failed. can't write to database.");
        }
    }

    public void addFailBackupJob(
            long syncFlowId,
            String errorMessage) throws SyncDuoException {
        if (StringUtils.isBlank(errorMessage)) {
            errorMessage = "";
        }
        BackupJobEntity backupJobEntity = new BackupJobEntity();
        // 设置失败状态和失败日志
        backupJobEntity.setSyncFlowId(syncFlowId);
        backupJobEntity.setBackupJobStatus(CopyJobStatusEnum.FAILED.name());
        backupJobEntity.setErrorMessage(errorMessage);
        // 保存
        boolean saved = this.save(backupJobEntity);
        if (!saved) {
            throw new SyncDuoException("addFailBackupJob failed. can't write to database.");
        }
    }

    public List<BackupJobEntity> getBySyncFlowId(long syncFlowId) {
        LambdaQueryWrapper<BackupJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BackupJobEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(BackupJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper);
    }
}
