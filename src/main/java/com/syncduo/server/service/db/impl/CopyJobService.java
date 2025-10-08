package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.CommonStatus;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.mapper.CopyJobMapper;
import com.syncduo.server.model.entity.CopyJobEntity;
import com.syncduo.server.model.rclone.core.stat.CoreStatsResponse;
import com.syncduo.server.model.rclone.job.status.JobStatusResponse;
import com.syncduo.server.service.db.ICopyJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

@Service
@Slf4j
public class CopyJobService
        extends ServiceImpl<CopyJobMapper, CopyJobEntity>
        implements ICopyJobService {

    public CopyJobEntity addCopyJob(long syncFlowId) throws DbException {
        CopyJobEntity copyJobEntity = new CopyJobEntity();
        copyJobEntity.setSyncFlowId(syncFlowId);
        // 设置默认值
        copyJobEntity.setCopyJobStatus(CommonStatus.RUNNING.name());
        // 保存数据库
        boolean saved = this.save(copyJobEntity);
        if (!saved) {
            throw new DbException("addCopyJob failed. save to database failed.");
        }
        return copyJobEntity;
    }

    public CopyJobEntity updateRcloneJobId(CopyJobEntity copyJobEntity, long rcloneJobId) throws DbException {
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobEntity.getCopyJobId());
        if (ObjectUtils.isEmpty(dbResult)) {
            throw new DbException("updateRcloneJobId failed. " +
                    "can't find by copyJobId:%s".formatted(copyJobEntity.getCopyJobId()));
        }
        dbResult.setRcloneJobId(rcloneJobId);
        boolean update = this.updateById(dbResult);
        if (!update) {
            throw new DbException("updateRcloneJobId failed. update to database failed.");
        }
        return dbResult;
    }

    public void markCopyJobAsSuccess(
            long copyJobId,
            JobStatusResponse jobStatusResponse) throws DbException {
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.setCopyJobStatus(CommonStatus.SUCCESS.name());
        // 时间转换
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(jobStatusResponse.getStartTime());
        Instant instant = offsetDateTime.toInstant();
        dbResult.setStartedAt(Timestamp.from(instant));
        // 时间转换
        offsetDateTime = OffsetDateTime.parse(jobStatusResponse.getStartTime());
        instant = offsetDateTime.toInstant();
        dbResult.setFinishedAt(Timestamp.from(instant));
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new DbException("markCopyJobAsSuccess failed. can't write to database.");
        }
    }

    public void updateSuccessCopyJobStat(long copyJobId, CoreStatsResponse coreStatsResponse) {
        if (ObjectUtils.isEmpty(coreStatsResponse)) {
            return;
        }
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobId);
        if (ObjectUtils.isEmpty(dbResult) || !CommonStatus.SUCCESS.name().equals(dbResult.getCopyJobStatus())) {
            return;
        }
        // 设置 stats
        dbResult.setTransferredFiles(coreStatsResponse.getTransfers());
        dbResult.setTransferredBytes(coreStatsResponse.getBytes());
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new DbException("markCopyJobAsSuccess failed. can't write to database.");
        }
    }

    public void markCopyJobAsFailed(
            long copyJobId,
            String errorMessage) throws ValidationException, DbException {
        if (ObjectUtils.isEmpty(copyJobId)) {
            throw new ValidationException("markCopyJobAsFailed failed. copyJobId is null.");
        }
        if (StringUtils.isBlank(errorMessage)) {
            errorMessage = "";
        }
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.setCopyJobStatus(CommonStatus.FAILED.name());
        dbResult.setErrorMessage(errorMessage);
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new DbException("markCopyJobAsFailed failed. can't write to database.");
        }
    }

    public CopyJobEntity getByCopyJobId(long copyJobId) {
        LambdaQueryWrapper<CopyJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CopyJobEntity::getCopyJobId, copyJobId);
        queryWrapper.eq(CopyJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper).stream().findFirst().orElse(null);
    }

    public CopyJobEntity getByRcloneJobId(long rcloneJobId) {
        LambdaQueryWrapper<CopyJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CopyJobEntity::getRcloneJobId, rcloneJobId);
        queryWrapper.eq(CopyJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper).stream().findFirst().orElse(null);
    }
}
