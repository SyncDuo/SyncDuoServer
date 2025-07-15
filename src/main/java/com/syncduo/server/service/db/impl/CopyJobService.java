package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.CopyJobStatusEnum;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
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

    public CopyJobEntity addCopyJob(long syncFlowId) throws SyncDuoException {
        CopyJobEntity copyJobEntity = new CopyJobEntity();
        copyJobEntity.setSyncFlowId(syncFlowId);
        // 设置默认值
        copyJobEntity.setCopyJobStatus(CopyJobStatusEnum.WAITING.name());
        // 保存数据库
        boolean saved = this.save(copyJobEntity);
        if (!saved) {
            throw new SyncDuoException("addCopyJob failed. save to database failed.");
        }
        return copyJobEntity;
    }

    public CopyJobEntity updateCopyJobRcloneJobId(
            long copyJobId,
            long rcloneJobId) throws SyncDuoException {
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return null;
        }
        dbResult.setCopyJobStatus(CopyJobStatusEnum.RUNNING.name());
        dbResult.setRcloneJobId(rcloneJobId);
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new SyncDuoException("updateCopyJobRcloneJobId failed. can't update database.");
        }
        return dbResult;
    }

    public void markCopyJobAsSuccess(
            long copyJobId,
            JobStatusResponse jobStatusResponse,
            CoreStatsResponse coreStatsResponse) throws SyncDuoException {
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.setCopyJobStatus(CopyJobStatusEnum.SUCCESS.name());
        // 时间转换
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(jobStatusResponse.getStartTime());
        Instant instant = offsetDateTime.toInstant();
        dbResult.setStartedAt(Timestamp.from(instant));
        // 时间转换
        offsetDateTime = OffsetDateTime.parse(jobStatusResponse.getStartTime());
        instant = offsetDateTime.toInstant();
        dbResult.setFinishedAt(Timestamp.from(instant));
        // 设置 stats, 容许 stats 为空, 因为有可能 core stats 获取失败, 但是 job 是成功的
        if (ObjectUtils.isNotEmpty(coreStatsResponse)) {
            dbResult.setTransferredFiles(coreStatsResponse.getTransfers());
            dbResult.setTransferredBytes(coreStatsResponse.getBytes());
        }
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new SyncDuoException("markCopyJobAsSuccess failed. can't write to database.");
        }
    }

    public void markCopyJobAsFailed(
            long copyJobId,
            String errorMessage) throws SyncDuoException {
        if (ObjectUtils.isEmpty(copyJobId)) {
            throw new SyncDuoException("failJob failed. copyJobId is null.");
        }
        if (StringUtils.isBlank(errorMessage)) {
            errorMessage = "";
        }
        CopyJobEntity dbResult = this.getByCopyJobId(copyJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.setCopyJobStatus(CopyJobStatusEnum.FAILED.name());
        dbResult.setErrorMessage(errorMessage);
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new SyncDuoException("failJob failed. can't write to database.");
        }
    }

    public CopyJobEntity getByCopyJobId(long copyJobId) throws SyncDuoException {
        LambdaQueryWrapper<CopyJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CopyJobEntity::getCopyJobId, copyJobId);
        queryWrapper.eq(CopyJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper).stream().findFirst().orElse(null);
    }

    public CopyJobEntity getByRcloneJobId(long rcloneJobId) throws SyncDuoException {
        LambdaQueryWrapper<CopyJobEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CopyJobEntity::getRcloneJobId, rcloneJobId);
        queryWrapper.eq(CopyJobEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());

        return this.list(queryWrapper).stream().findFirst().orElse(null);
    }
}
