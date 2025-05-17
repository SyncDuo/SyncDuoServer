package com.syncduo.server.service.bussiness.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncFlowMapper;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.internal.JoinResult;
import com.syncduo.server.service.bussiness.ISyncFlowService;
import com.syncduo.server.util.JoinUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SyncFlowService
        extends ServiceImpl<SyncFlowMapper, SyncFlowEntity>
        implements ISyncFlowService {

    protected SyncFlowEntity createSyncFlowDB(
            Long sourceFolderId,
            Long destFolderId,
            SyncFlowTypeEnum syncFlowType,
            String syncFlowName) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId, destFolderId, syncFlowType)) {
            throw new SyncDuoException("createSyncFlow failed. " +
                    "sourceFolderId, destFolderId or syncFlowType is null.");
        }
        if (StringUtils.isBlank(syncFlowName)) {
            throw new SyncDuoException("createSyncFlow failed, syncFlowName is null");
        }
        SyncFlowEntity dbResult = this.getBySourceFolderIdAndDestDB(sourceFolderId, destFolderId);
        if (ObjectUtils.isEmpty(dbResult)) {
            // 创建 sync flow
            dbResult = new SyncFlowEntity();
            dbResult.setSourceFolderId(sourceFolderId);
            dbResult.setDestFolderId(destFolderId);
            dbResult.setSyncFlowType(syncFlowType.name());
            dbResult.setSyncFlowName(syncFlowName);
            dbResult.setSyncStatus(SyncFlowStatusEnum.NOT_SYNC.name());
            boolean saved = this.save(dbResult);
            if (!saved) {
                throw new SyncDuoException("创建 Sync Flow 失败, 无法保存到数据库");
            }
        }
        // 返回
        return dbResult;
    }

    protected SyncFlowEntity getBySourceFolderIdAndDestDB(Long sourceFolderId, Long destFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId, destFolderId)) {
            throw new SyncDuoException("getBySourceFolderIdAndDestDB failed. sourceFolderId 或 destFolderId 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderId, sourceFolderId);
        queryWrapper.eq(SyncFlowEntity::getDestFolderId, destFolderId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    protected List<SyncFlowEntity> getBySourceFolderIdDB(Long sourceFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId)) {
            throw new SyncDuoException("getBySourceFolderIdDB failed, sourceFolderId is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderId, sourceFolderId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.list(queryWrapper);
    }


    protected SyncFlowEntity getBySyncFlowIdDB(Long syncFlowId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowId)) {
            throw new SyncDuoException("getBySyncFlowIdDB failed, syncFlowId is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.getOne(queryWrapper);
    }

    protected List<SyncFlowEntity> getBySyncFlowStatusDB(SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowStatusEnum 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncStatus, syncFlowStatusEnum.name());
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    protected List<SyncFlowEntity> getAllSyncFlowDB() {
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    protected SyncFlowEntity updateSyncFlowStatusDB(
            SyncFlowEntity syncFlowEntity,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowEntity, syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowEntity 或 syncFlowStatusEnum 为空");
        }
        return this.updateSyncFlowStatusDB(syncFlowEntity.getSyncFlowId(), syncFlowStatusEnum);
    }

    protected SyncFlowEntity updateSyncFlowStatusDB(
            Long syncFlowId,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId, syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowId 或 syncFlowStatusEnum 为空");
        }
        SyncFlowEntity dbResult = this.getBySyncFlowIdDB(syncFlowId);
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

    protected void deleteSyncFlowDB(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 检查参数
        if (ObjectUtils.isEmpty(syncFlowEntity) || ObjectUtils.isEmpty(syncFlowEntity.getSyncFlowId())) {
            throw new SyncDuoException("syncFlowEntity 或 getSyncFlowId 为空");
        }
        // 先找到记录
        SyncFlowEntity dbResult = this.getBySyncFlowIdDB(syncFlowEntity.getSyncFlowId());
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
}
