package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.CommonStatus;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.exception.ResourceNotFoundException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.mapper.RestoreJobMapper;
import com.syncduo.server.model.entity.RestoreJobEntity;
import com.syncduo.server.model.restic.restore.RestoreSummary;
import com.syncduo.server.service.db.IRestoreJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RestoreJobService
        extends ServiceImpl<RestoreJobMapper, RestoreJobEntity>
        implements IRestoreJobService {

    public RestoreJobEntity addRunningRestoreJob(
            String snapshotId,
            String restoreRootPath) throws DbException {
        RestoreJobEntity restoreJobEntity = new RestoreJobEntity();
        restoreJobEntity.setRestoreJobStatus(CommonStatus.RUNNING.name());
        restoreJobEntity.setSnapshotId(snapshotId);
        restoreJobEntity.setRestoreRootPath(restoreRootPath);
        // 保存
        boolean saved = this.save(restoreJobEntity);
        if (!saved) {
            throw new DbException("addRunningRestoreJob failed. can't write to database.");
        }
        return restoreJobEntity;
    }

    public void updateRestoreJobAsSuccess(
            long restoreJobId,
            RestoreSummary restoreSummary) throws ValidationException, DbException {
        RestoreJobEntity dbResult = this.getByRestoreJobId(restoreJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            throw new ResourceNotFoundException("restore job:%s does not exist.".formatted(restoreJobId));
        }
        // 设置失败状态和失败日志
        dbResult.setRestoreJobStatus(CommonStatus.SUCCESS.name());
        dbResult.setSecondsElapsed(restoreSummary.getSecondsElapsed());
        dbResult.setRestoreFiles(restoreSummary.getFilesRestored());
        dbResult.setRestoreBytes(restoreSummary.getBytesRestored());
        // 保存
        boolean saved = this.updateById(dbResult);
        if (!saved) {
            throw new DbException("updateRestoreJobAsSuccess failed. can't write to database.");
        }
    }

    public void updateRestoreJobAsFailed(
            long restoreJobId,
            String errorMessage) throws ValidationException, DbException {
        RestoreJobEntity dbResult = this.getByRestoreJobId(restoreJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            throw new ResourceNotFoundException("restore job:%s does not exist.".formatted(restoreJobId));
        }
        errorMessage = StringUtils.isBlank(errorMessage) ? "" : errorMessage;
        // 设置失败状态和失败日志
        dbResult.setRestoreJobStatus(CommonStatus.FAILED.name());
        dbResult.setErrorMessage(errorMessage);
        // 保存
        boolean saved = this.updateById(dbResult);
        if (!saved) {
            throw new DbException("updateRestoreJobAsFailed failed. can't write to database.");
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
