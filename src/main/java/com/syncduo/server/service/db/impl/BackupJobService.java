package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.CommonStatus;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.mapper.BackupJobMapper;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.restic.backup.BackupSummary;
import com.syncduo.server.service.db.IBackupJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class BackupJobService
        extends ServiceImpl<BackupJobMapper, BackupJobEntity>
        implements IBackupJobService {

    public BackupJobEntity addBackupJob(long syncFlowId) throws DbException {
        BackupJobEntity backupJobEntity = new BackupJobEntity();
        backupJobEntity.setSyncFlowId(syncFlowId);
        backupJobEntity.setBackupJobStatus(CommonStatus.RUNNING.name());
        boolean saved = this.save(backupJobEntity);
        if (!saved) {
            throw new DbException("addBackupJob failed. can't write to database.");
        }
        return backupJobEntity;
    }

    public void updateSuccessBackupJob(
            BackupJobEntity backupJobEntity,
            BackupSummary backupSummary) throws DbException {
        backupJobEntity.setBackupJobStatus(CommonStatus.SUCCESS.name());
        backupJobEntity.setSuccessMessage(backupSummary.toString());
        // 有 snapshot id 则设置相关信息, 没有则直接更新
        if (StringUtils.isNotBlank(backupSummary.getSnapshotId())) {
            // 设置 snapshot id
            backupJobEntity.setSnapshotId(backupSummary.getSnapshotId());
            // 设置时间
            backupJobEntity.setStartedAt(Timestamp.from(backupSummary.getBackupStart()));
            backupJobEntity.setFinishedAt(Timestamp.from(backupSummary.getBackupEnd()));
            // 设置 stats
            backupJobEntity.setBackupFiles(backupSummary.getTotalFilesProcessed());
            backupJobEntity.setBackupBytes(backupSummary.getTotalBytesProcessed());
        }
        // 更新
        boolean updated = this.updateById(backupJobEntity);
        if (!updated) {
            throw new DbException("updateSuccessBackupJob failed. can't write to database.");
        }
    }

    public void updateFailBackupJob(
            BackupJobEntity backupJobEntity,
            String errorMessage) throws DbException {
        if (StringUtils.isBlank(errorMessage)) {
            errorMessage = "";
        }
        // 设置失败状态和失败日志
        backupJobEntity.setBackupJobStatus(CommonStatus.FAILED.name());
        backupJobEntity.setErrorMessage(errorMessage);
        // 保存
        boolean updated = this.updateById(backupJobEntity);
        if (!updated) {
            throw new DbException("updateFailBackupJob failed. can't write to database.");
        }
    }

    public BackupJobEntity getByBackupJobId(long backupJobId) {
        LambdaQueryWrapper<BackupJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BackupJobEntity::getBackupJobId, backupJobId);
        queryWrapper.eq(BackupJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        List<BackupJobEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public BackupJobEntity getFirstValidSnapshotId(BackupJobEntity current) {
        LambdaQueryWrapper<BackupJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(BackupJobEntity::getSnapshotId);
        queryWrapper.le(BackupJobEntity::getLastUpdatedTime, current.getLastUpdatedTime());
        queryWrapper.eq(BackupJobEntity::getBackupJobStatus, CommonStatus.SUCCESS.name());
        queryWrapper.eq(BackupJobEntity::getSyncFlowId, current.getSyncFlowId());
        queryWrapper.eq(BackupJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        List<BackupJobEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? current : dbResult.get(0);
    }

    public List<BackupJobEntity> getBySyncFlowId(long syncFlowId) {
        LambdaQueryWrapper<BackupJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BackupJobEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(BackupJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper);
    }
}
