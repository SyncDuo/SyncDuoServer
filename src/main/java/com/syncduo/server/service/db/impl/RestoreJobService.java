package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.RestoreJobStatusEnum;
import com.syncduo.server.enums.CopyJobStatusEnum;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.RestoreJobMapper;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.entity.RestoreJobEntity;
import com.syncduo.server.model.restic.backup.BackupSummary;
import com.syncduo.server.model.restic.restore.RestoreSummary;
import com.syncduo.server.service.db.IRestoreJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class RestoreJobService
        extends ServiceImpl<RestoreJobMapper, RestoreJobEntity>
        implements IRestoreJobService {

    public RestoreJobEntity addRunningRestoreJob(
            String snapshotId,
            String restoreRootPath) throws SyncDuoException {
        RestoreJobEntity restoreJobEntity = new RestoreJobEntity();
        restoreJobEntity.setRestoreJobStatus(RestoreJobStatusEnum.RUNNING.name());
        restoreJobEntity.setSnapshotId(snapshotId);
        restoreJobEntity.setRestoreRootPath(restoreRootPath);
        // 保存
        boolean saved = this.save(restoreJobEntity);
        if (!saved) {
            throw new SyncDuoException("addRunningRestoreJob failed. can't write to database.");
        }
        return restoreJobEntity;
    }

    public void updateRestoreJobAsSuccess(
            long restoreJobId,
            RestoreSummary restoreSummary) throws SyncDuoException {
        RestoreJobEntity dbResult = this.getByRestoreJobId(restoreJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            log.warn("restore job not found. id is {}", restoreJobId);
            return;
        }
        // 设置失败状态和失败日志
        dbResult.setRestoreJobStatus(CopyJobStatusEnum.SUCCESS.name());
        dbResult.setSecondsElapsed(restoreSummary.getSecondsElapsed());
        dbResult.setRestoreFiles(restoreSummary.getFilesRestored());
        dbResult.setRestoreBytes(restoreSummary.getBytesRestored());
        // 保存
        boolean saved = this.updateById(dbResult);
        if (!saved) {
            throw new SyncDuoException("updateRestoreJobAsSuccess failed. can't write to database.");
        }
    }

    public void updateRestoreJobAsFailed(
            long restoreJobId,
            String errorMessage) throws SyncDuoException {
        RestoreJobEntity dbResult = this.getByRestoreJobId(restoreJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            log.warn("restore job not found. id is {}", restoreJobId);
            return;
        }
        errorMessage = StringUtils.isBlank(errorMessage) ? "" : errorMessage;
        // 设置失败状态和失败日志
        dbResult.setRestoreJobStatus(CopyJobStatusEnum.FAILED.name());
        dbResult.setErrorMessage(errorMessage);
        // 保存
        boolean saved = this.updateById(dbResult);
        if (!saved) {
            throw new SyncDuoException("updateRestoreJobAsFailed failed. can't write to database.");
        }
    }

    public RestoreJobEntity getByRestoreJobId(long RestoreJobId) {
        LambdaQueryWrapper<RestoreJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RestoreJobEntity::getRestoreJobId, RestoreJobId);
        queryWrapper.eq(RestoreJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        List<RestoreJobEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public List<RestoreJobEntity> getBySnapshotId(String snapshotId) {
        LambdaQueryWrapper<RestoreJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RestoreJobEntity::getSnapshotId, snapshotId);
        queryWrapper.eq(RestoreJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper);
    }
}
