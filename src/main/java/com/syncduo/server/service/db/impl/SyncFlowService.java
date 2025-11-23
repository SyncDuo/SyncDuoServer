package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.exception.JsonException;
import com.syncduo.server.exception.ValidationException;
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
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class SyncFlowService
        extends ServiceImpl<SyncFlowMapper, SyncFlowEntity>
        implements ISyncFlowService {

    public SyncFlowEntity createSyncFlow(CreateSyncFlowRequest createSyncFlowRequest) throws DbException {
        // 检查是否重名
        String syncFlowName = createSyncFlowRequest.getSyncFlowName();
        SyncFlowEntity dbResult = this.getBySyncFlowName(syncFlowName);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            throw new DbException("createSyncFlow failed. syncFlowName is duplicate");
        }
        // 检查 syncflow 是否重复
        String sourceFolderPath = createSyncFlowRequest.getSourceFolderFullPath();
        String destFolderPath = createSyncFlowRequest.getDestFolderFullPath();
        dbResult = this.getBySourceAndDestFolderPath(sourceFolderPath, destFolderPath);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            throw new DbException("createSyncFlow failed. " +
                    "sourceFolderPath:%s and destFolderPath:%s already created.".formatted(
                            sourceFolderPath, destFolderPath));
        }
        // 创建
        SyncFlowEntity syncFlowEntity = new SyncFlowEntity();
        syncFlowEntity.setSyncFlowName(syncFlowName);
        syncFlowEntity.setSourceFolderPath(sourceFolderPath);
        syncFlowEntity.setDestFolderPath(destFolderPath);
        // 如果是 backup only, 则状态永远都是 SYNC
        if (createSyncFlowRequest.getSyncFlowType().equals(SyncFlowTypeEnum.BACKUP_ONLY.getType())) {
            syncFlowEntity.setSyncStatus(SyncFlowStatusEnum.BACKUP_ONLY_SYNC.name());
            syncFlowEntity.setLastSyncTime(Timestamp.from(Instant.now()));
        } else {
            syncFlowEntity.setSyncStatus(SyncFlowStatusEnum.INITIAL_SCAN.name());
            syncFlowEntity.setLastSyncTime(null);
        }
        syncFlowEntity.setFilterCriteria(createSyncFlowRequest.getFilterCriteria());
        syncFlowEntity.setSyncFlowType(createSyncFlowRequest.getSyncFlowType());
        this.save(syncFlowEntity);
        return syncFlowEntity;
    }

    public SyncFlowEntity getBySyncFlowId(Long syncFlowId) throws ValidationException {
        if (ObjectUtils.isEmpty(syncFlowId)) {
            throw new ValidationException("getBySyncFlowIdDB failed, syncFlowId is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.getOne(queryWrapper);
    }

    public SyncFlowEntity getBySyncFlowName(String syncFlowName) throws ValidationException {
        if (StringUtils.isBlank(syncFlowName)) {
            throw new ValidationException("getBySyncFlowName failed. syncFlowName is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowName, syncFlowName);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.getOne(queryWrapper);
    }

    public List<SyncFlowEntity> getAllSyncFlow() {
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public SyncFlowEntity updateSyncFlowStatus(
            SyncFlowEntity syncFlowEntity,
            SyncFlowStatusEnum syncFlowStatusEnum)
            throws ValidationException, DbException {
        if (ObjectUtils.anyNull(syncFlowEntity, syncFlowStatusEnum)) {
            throw new ValidationException("syncFlowEntity 或 syncFlowStatusEnum 为空");
        }
        SyncFlowEntity dbResult = this.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (ObjectUtils.isEmpty(dbResult)) {
            return null;
        }
        dbResult.setSyncStatus(syncFlowStatusEnum.name());
        if (syncFlowStatusEnum == SyncFlowStatusEnum.SYNC) {
            dbResult.setLastSyncTime(Timestamp.from(Instant.now()));
        }
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new DbException("更新失败. dbResult 为 %s".formatted(dbResult));
        }
        return dbResult;
    }

    public SyncFlowEntity getBySourceAndDestFolderPath(
            String sourceFolderPath,
            String destFolderPath
    ) throws ValidationException {
        if (StringUtils.isAnyBlank(sourceFolderPath, destFolderPath)) {
            throw new ValidationException("getBySourceAndDestFolderPath failed." +
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

    public List<SyncFlowEntity> getBySourceFolderPath(String sourceFolderPath) throws ValidationException {
        if (StringUtils.isAnyBlank(sourceFolderPath)) {
            throw new ValidationException("getBySourceFolderPath failed." +
                    "sourceFolderPath:%s is null.".formatted(sourceFolderPath));
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderPath, sourceFolderPath);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return Collections.emptyList();
        }
        return dbResult;
    }

    public void deleteSyncFlow(SyncFlowEntity syncFlowEntity)
            throws ValidationException, DbException {
        // 检查参数
        if (ObjectUtils.anyNull(syncFlowEntity, syncFlowEntity.getSyncFlowId())) {
            throw new ValidationException("deleteSyncFlow failed. syncFlowEntity or syncFlowId is null");
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
            throw new DbException("deleteSyncFlow failed. can't write to database");
        }
    }

    public List<String> getFilterCriteriaAsList(SyncFlowEntity syncFlowEntity)
            throws ValidationException, JsonException {
        EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        return JsonUtil.deserializeStringToList(syncFlowEntity.getFilterCriteria());
    }
}
