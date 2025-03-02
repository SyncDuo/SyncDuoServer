package com.syncduo.server.service.bussiness.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.bus.FileAccessValidator;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncFlowMapper;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.internal.JoinResult;
import com.syncduo.server.service.bussiness.ISyncFlowService;
import com.syncduo.server.util.JoinUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SyncFlowService
        extends ServiceImpl<SyncFlowMapper, SyncFlowEntity>
        implements ISyncFlowService {

    // <folderId, syncFlowStatus>
    private final Map<Long, List<SyncFlowStatus>> cacheMap = new ConcurrentHashMap<>();

    public List<SyncFlowEntity> getBySourceIdFromCache(Long folderId) throws SyncDuoException {
        List<SyncFlowStatus> syncFlowStatusList = this.doubleQueryCache(folderId);
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            return Collections.emptyList();
        }
        return syncFlowStatusList.stream().map(SyncFlowStatus::getSyncFlowEntity).collect(Collectors.toList());
    }

    private List<SyncFlowStatus> doubleQueryCache(Long folderId) throws SyncDuoException {
        // 找到 folder id 为 source id, 对应的 syncFlowId
        List<SyncFlowStatus> syncFlowStatusList = this.cacheMap.get(folderId);
        // 如果 cache map 为空, 则从数据库查询一次, 如果仍然为空, 则返回
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            this.validCacheMap(folderId);
            syncFlowStatusList = this.cacheMap.get(folderId);
            if (CollectionUtils.isEmpty(syncFlowStatusList)) {
                return Collections.emptyList();
            }
        }
        return syncFlowStatusList;
    }

    private void validCacheMap(Long folderId) throws SyncDuoException {
        List<SyncFlowEntity> dbResult = this.getBySourceFolderId(folderId);
        if (CollectionUtils.isEmpty(dbResult)) {
            cacheMap.remove(folderId);
            return;
        }
        List<SyncFlowStatus> syncFlowStatusList = cacheMap.get(folderId);
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            for (SyncFlowEntity syncFlowEntity : dbResult) {
                syncFlowStatusList.add(new SyncFlowStatus(syncFlowEntity, 0));
            }
        } else {
            // 计算 join
            JoinResult<SyncFlowEntity, SyncFlowStatus> joinResult = JoinUtil.allJoin(
                    dbResult,
                    syncFlowStatusList,
                    SyncFlowEntity::getSyncFlowId,
                    syncFlowStatus -> syncFlowStatus.getSyncFlowEntity().getSyncFlowId()
            );
            // 左外集则需要添加 cache map
            for (SyncFlowEntity syncFlowEntity : joinResult.getLeftOuterResult()) {
                syncFlowStatusList.add(new SyncFlowStatus(syncFlowEntity, 0));
            }
            // 右外集则需要删除 cache map
            syncFlowStatusList.removeAll(joinResult.getRightOuterResult());
        }
        cacheMap.put(folderId, syncFlowStatusList);
    }

    public void addPendingEventCount(Long folderId) throws SyncDuoException {
        // 找到 folder id 为 source id, 对应的 syncFlowId
        List<SyncFlowStatus> syncFlowStatusList = this.doubleQueryCache(folderId);
        for (SyncFlowStatus syncFlowStatus : syncFlowStatusList) {
            syncFlowStatus.addPendingEventCount();
        }
    }

    public void decrPendingEventCount(Long folderId) {
        // 找到 folder id 为 source id, 对应的 syncFlowId
        List<SyncFlowStatus> syncFlowStatusList = this.cacheMap.get(folderId);
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            return;
        }
        for (SyncFlowStatus syncFlowStatus : syncFlowStatusList) {
            syncFlowStatus.decrPendingEventCount();
        }
    }

    public SyncFlowEntity createSyncFlow(
            Long sourceFolderId, Long destFolderId, SyncFlowTypeEnum syncFlowType) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId, destFolderId, syncFlowType)) {
            throw new SyncDuoException("创建 Sync Flow 失败, sourceFolderId, destFolderId 或 syncFlowType 为空. %s");
        }
        SyncFlowEntity dbResult = this.getBySourceFolderIdAndDest(sourceFolderId, destFolderId);
        if (ObjectUtils.isEmpty(dbResult)) {
            // 创建 sync flow
            dbResult = new SyncFlowEntity();
            dbResult.setSourceFolderId(sourceFolderId);
            dbResult.setDestFolderId(destFolderId);
            dbResult.setSyncFlowType(syncFlowType.name());
            dbResult.setSyncStatus(SyncFlowStatusEnum.NOT_SYNC.name());
            boolean saved = this.save(dbResult);
            if (!saved) {
                throw new SyncDuoException("创建 Sync Flow 失败, 无法保存到数据库");
            }
        }
        // sync-flow 添加到 map
        List<SyncFlowStatus> syncFlowStatusList = cacheMap.getOrDefault(sourceFolderId, new ArrayList<>(1));
        syncFlowStatusList.add(new SyncFlowStatus(dbResult, 0));
        cacheMap.put(sourceFolderId, syncFlowStatusList);
        // 返回
        return dbResult;
    }

    public SyncFlowEntity getBySourceFolderIdAndDest(Long sourceFolderId, Long destFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId, destFolderId)) {
            throw new SyncDuoException("查找 Sync Flow 失败, sourceFolderId 或 destFolderId 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderId, sourceFolderId);
        queryWrapper.eq(SyncFlowEntity::getDestFolderId, destFolderId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    private List<SyncFlowEntity> getBySourceFolderId(Long sourceFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId)) {
            throw new SyncDuoException("getBySourceFolderId failed, sourceFolderId is null");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderId, sourceFolderId);
        queryWrapper.eq(SyncFlowEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.list(queryWrapper);
    }

    public List<SyncFlowEntity> getBySyncFlowStatus(SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
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

    public void updateSyncFlowStatus(
            SyncFlowEntity syncFlowEntity,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowEntity, syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowEntity 或 syncFlowStatusEnum 为空");
        }
        syncFlowEntity.setSyncStatus(syncFlowStatusEnum.name());
        if (syncFlowStatusEnum == SyncFlowStatusEnum.SYNC) {
            syncFlowEntity.setLastSyncTime(Timestamp.from(Instant.now()));
        }
        boolean updated = this.updateById(syncFlowEntity);
        if (!updated) {
            throw new SyncDuoException("更新失败. sync-flow entity 为 %s".formatted(syncFlowEntity));
        }
    }

    public void updateSyncFlowStatus(
            Long syncFlowId,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId, syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowId 或 syncFlowStatusEnum 为空");
        }
        LambdaUpdateWrapper<SyncFlowEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SyncFlowEntity::getSyncFlowId, syncFlowId);
        updateWrapper.set(SyncFlowEntity::getSyncStatus, syncFlowStatusEnum.name());
        if (syncFlowStatusEnum == SyncFlowStatusEnum.SYNC) {
            updateWrapper.set(SyncFlowEntity::getLastSyncTime, Timestamp.from(Instant.now()));
        }
        boolean updated = this.update(updateWrapper);
        if (!updated) {
            throw new SyncDuoException("更新失败. sync-flow id 为 %s".formatted(syncFlowId));
        }
    }

    public void deleteSyncFlow(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 检查参数
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new SyncDuoException("syncFlowEntity is null");
        }
        // 删除 sync-flow
        syncFlowEntity.setRecordDeleted(DeletedEnum.DELETED.getCode());
        boolean isDeleted = this.updateById(syncFlowEntity);
        if (!isDeleted) {
            throw new SyncDuoException("删除 sync-flow 失败");
        }
        // 从 map 中删除
        List<SyncFlowStatus> syncFlowStatusList = cacheMap.get(syncFlowEntity.getSyncFlowId());
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            return;
        }
        syncFlowStatusList.removeIf(
                syncFlowStatus -> syncFlowStatus
                        .getSyncFlowEntity()
                        .getSyncFlowId()
                        .equals(syncFlowEntity.getSyncFlowId()));
    }

    @AllArgsConstructor
    @Data
    static class SyncFlowStatus {

        private SyncFlowEntity syncFlowEntity;

        private Integer pendingEventCount;

        public void addPendingEventCount() {
            this.pendingEventCount++;
        }

        public void decrPendingEventCount() {
            this.pendingEventCount--;
        }
    }
}
