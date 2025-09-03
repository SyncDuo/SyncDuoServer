package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncFlowMapper;
import com.syncduo.server.model.api.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.db.ISyncFlowService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class SyncFlowService
        extends ServiceImpl<SyncFlowMapper, SyncFlowEntity>
        implements ISyncFlowService {

    public SyncFlowEntity createSyncFlow(CreateSyncFlowRequest createSyncFlowRequest) throws SyncDuoException {
        // 检查是否重名
        String syncFlowName = createSyncFlowRequest.getSyncFlowName();
        SyncFlowEntity dbResult = this.getBySyncFlowName(syncFlowName);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            throw new SyncDuoException("createSyncFlow failed. syncFlowName is duplicate");
        }
        // 检查 syncflow 是否重复
        String sourceFolderPath = createSyncFlowRequest.getSourceFolderFullPath();
        String destFolderPath = createSyncFlowRequest.getDestFolderFullPath();
        dbResult = this.getBySourceAndDestFolderPath(sourceFolderPath, destFolderPath);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            throw new SyncDuoException("createSyncFlow failed. " +
                    "sourceFolderPath:%s and destFolderPath:%s already created.".formatted(
                            sourceFolderPath, destFolderPath));
        }
        // 创建
        SyncFlowEntity syncFlowEntity = new SyncFlowEntity();
        syncFlowEntity.setSyncFlowName(syncFlowName);
        syncFlowEntity.setSourceFolderPath(sourceFolderPath);
        syncFlowEntity.setDestFolderPath(destFolderPath);
        syncFlowEntity.setSyncStatus(SyncFlowStatusEnum.RUNNING.name());
        syncFlowEntity.setLastSyncTime(null);
        syncFlowEntity.setFilterCriteria(createSyncFlowRequest.getFilterCriteria());
        this.save(syncFlowEntity);
        return syncFlowEntity;
    }

    public SyncFlowEntity getBySyncFlowId(Long syncFlowId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowId)) {
            throw new SyncDuoException("getBySyncFlowIdDB failed, syncFlowId is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.getOne(queryWrapper);
    }

    public SyncFlowEntity getBySyncFlowName(String syncFlowName) throws SyncDuoException {
        if (StringUtils.isBlank(syncFlowName)) {
            throw new SyncDuoException("getBySyncFlowName failed. syncFlowName is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowName, syncFlowName);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.getOne(queryWrapper);
    }

    public List<SyncFlowEntity> getBySyncFlowStatus(
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowStatusEnum 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncStatus, syncFlowStatusEnum.name());
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public List<SyncFlowEntity> getAllSyncFlow() {
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public SyncFlowEntity updateSyncFlowStatus(
            SyncFlowEntity syncFlowEntity,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowEntity, syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowEntity 或 syncFlowStatusEnum 为空");
        }
        return this.updateSyncFlowStatus(syncFlowEntity.getSyncFlowId(), syncFlowStatusEnum);
    }

    public SyncFlowEntity updateSyncFlowStatus(
            Long syncFlowId,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId, syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowId 或 syncFlowStatusEnum 为空");
        }
        SyncFlowEntity dbResult = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return null;
        }
        dbResult.setSyncStatus(syncFlowStatusEnum.name());
        if (syncFlowStatusEnum == SyncFlowStatusEnum.SYNC) {
            dbResult.setLastSyncTime(Timestamp.from(Instant.now()));
        }
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new SyncDuoException("更新失败. dbResult 为 %s".formatted(dbResult));
        }
        return dbResult;
    }

    public SyncFlowEntity getBySourceAndDestFolderPath(
            String sourceFolderPath,
            String destFolderPath
    ) throws SyncDuoException {
        if (StringUtils.isAnyBlank(sourceFolderPath, destFolderPath)) {
            throw new SyncDuoException("getBySourceAndDestFolderPath failed." +
                    "sourceFolderPath:%s or destFolderPath:%s is null.".formatted(sourceFolderPath, destFolderPath));
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderPath, sourceFolderPath);
        queryWrapper.eq(SyncFlowEntity::getDestFolderPath, destFolderPath);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return null;
        }
        return dbResult.get(0);
    }

    public List<SyncFlowEntity> getBySourceFolderPath(
            String sourceFolderPath,
            boolean ignorePausedSyncFlow) throws SyncDuoException {
        if (StringUtils.isAnyBlank(sourceFolderPath)) {
            throw new SyncDuoException("getBySourceFolderPath failed." +
                    "sourceFolderPath:%s is null.".formatted(sourceFolderPath));
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderPath, sourceFolderPath);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        if (ignorePausedSyncFlow) {
            queryWrapper.in(SyncFlowEntity::getSyncStatus, SyncFlowStatusEnum.getNotPauseStatus());
        }
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return Collections.emptyList();
        }
        return dbResult;
    }

    public void deleteSyncFlow(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 检查参数
        if (ObjectUtils.anyNull(syncFlowEntity, syncFlowEntity.getSyncFlowId())) {
            throw new SyncDuoException("deleteSyncFlow failed. syncFlowEntity or syncFlowId is null");
        }
        // 先找到记录
        SyncFlowEntity dbResult = this.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        // 删除 sync-flow
        dbResult.setRecordDeleted(DeletedEnum.DELETED.getCode());
        boolean isDeleted = this.updateById(dbResult);
        if (!isDeleted) {
            throw new SyncDuoException("deleteSyncFlow failed." +
                    "can't write to database");
        }
    }

    public List<String> getFilterCriteriaAsList(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        return JsonUtil.deserializeStringToList(syncFlowEntity.getFilterCriteria());
    }
}
