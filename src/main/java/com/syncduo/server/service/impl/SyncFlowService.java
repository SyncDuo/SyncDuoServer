package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncFlowMapper;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.ISyncFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SyncFlowService
        extends ServiceImpl<SyncFlowMapper, SyncFlowEntity>
        implements ISyncFlowService {

    // map<sync-flow-id, event number wait for handle
    private final ConcurrentHashMap<Long, Integer> eventCountMap = new ConcurrentHashMap<>(1000);

    public void initEventCountMap(Long syncFlowId) {
        this.eventCountMap.putIfAbsent(syncFlowId, 0);
    }

    public void incrSource2InternalEventCount(Long sourceFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId)) {
            throw new SyncDuoException("sourceFolderId is null");
        }
        SyncFlowEntity source2InternalSyncFlow = this.getSource2InternalSyncFlowByFolderId(sourceFolderId);
        Integer eventCount = this.eventCountMap.getOrDefault(source2InternalSyncFlow.getSyncFlowId(), 0);
        this.eventCountMap.put(source2InternalSyncFlow.getSyncFlowId(), eventCount + 1);
    }

    public void decrSource2InternalEventCount(Long sourceFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId)) {
            throw new SyncDuoException("sourceFolderId is null");
        }
        SyncFlowEntity syncFlowEntity = this.getSource2InternalSyncFlowByFolderId(sourceFolderId);
        if (!eventCountMap.containsKey(syncFlowEntity.getSyncFlowId())) {
            throw new SyncDuoException(
                    "missing syncFlowId in eventCountMap. sourceFolderId is %s".formatted(sourceFolderId));
        }
        Integer eventCount = this.eventCountMap.get(syncFlowEntity.getSyncFlowId());
        if (eventCount <= 0) {
            throw new SyncDuoException("event count is already zero. syncFlowEntity is %s".formatted(syncFlowEntity));
        }
        this.eventCountMap.put(syncFlowEntity.getSyncFlowId(), eventCount - 1);
        if (eventCount == 1) {
            this.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
        }
    }

    public void incrInternal2ContentEventCount(Long internalFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(internalFolderId)) {
            throw new SyncDuoException("sourceFolderId is null");
        }
        List<SyncFlowEntity> internal2ContentSyncFlowList =
                this.getInternal2ContentSyncFlowListByFolderId(internalFolderId);
        if (CollectionUtils.isEmpty(internal2ContentSyncFlowList)) {
            throw new SyncDuoException(
                    "missing internal2Content SyncFlow. internalFolder is %s".formatted(internalFolderId));
        }
        for (SyncFlowEntity syncFlowEntity : internal2ContentSyncFlowList) {
            Integer eventCount = this.eventCountMap.getOrDefault(syncFlowEntity.getSyncFlowId(), 0);
            this.eventCountMap.put(syncFlowEntity.getSyncFlowId(), eventCount + 1);
        }
    }

    public void decrInternal2ContentEventCount(Long destFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(destFolderId)) {
            throw new SyncDuoException("destFolderId is null");
        }
        SyncFlowEntity syncFlowEntity = this.getInternal2ContentSyncFlowByFolderId(destFolderId);
        if (!eventCountMap.containsKey(syncFlowEntity.getSyncFlowId())) {
            throw new SyncDuoException(
                    "missing syncFlowId in eventCountMap. destFolderId is %s".formatted(destFolderId));
        }
        Integer eventCount = this.eventCountMap.get(syncFlowEntity.getSyncFlowId());
        if (eventCount <= 0) {
            throw new SyncDuoException("event count is already zero. syncFlowEntity is %s".formatted(syncFlowEntity));
        }
        this.eventCountMap.put(syncFlowEntity.getSyncFlowId(), eventCount - 1);
        if (eventCount == 1) {
            this.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
        }
    }

    public SyncFlowEntity createSyncFlow(
            String syncFlowName,
            Long sourceFolderId,
            Long destFolderId,
            SyncFlowTypeEnum syncFlowType) throws SyncDuoException {
        if (ObjectUtils.anyNull(sourceFolderId, destFolderId, syncFlowType)) {
            throw new SyncDuoException("创建 Sync Flow 失败, sourceFolderId, destFolderId 或 syncFlowType 为空. %s");
        }
        if (StringUtils.isBlank(syncFlowName)) {
            throw new SyncDuoException("createSyncFlow failed. syncFlowName is null");
        }
        SyncFlowEntity dbResult = this.getBySourceFolderIdAndDest(sourceFolderId, destFolderId);
        if (ObjectUtils.isEmpty(dbResult)) {
            // 创建 sync flow
            dbResult = new SyncFlowEntity();
            dbResult.setSyncFlowName(syncFlowName);
            dbResult.setSourceFolderId(sourceFolderId);
            dbResult.setDestFolderId(destFolderId);
            dbResult.setSyncFlowType(syncFlowType.name());
            boolean saved = this.save(dbResult);
            if (!saved) {
                throw new SyncDuoException("创建 Sync Flow 失败, 无法保存到数据库");
            }
        }
        // sync-flow 添加到 eventCountMap
        this.eventCountMap.put(dbResult.getSyncFlowId(), 0);
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
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public SyncFlowEntity getSource2InternalSyncFlowByFolderId(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("获取 Sync Flow 失败, folderId 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderId, folderId);
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
        queryWrapper.eq(SyncFlowEntity::getSyncFlowType, SyncFlowTypeEnum.SOURCE_TO_INTERNAL);
        List<SyncFlowEntity> dbResult = this.baseMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return null;
        }
        if (dbResult.size() > 1) {
            throw new SyncDuoException("source->internal 出现一对多");
        }
        return dbResult.get(0);
    }

    public List<SyncFlowEntity> getInternal2ContentSyncFlowListByFolderId(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("获取 Sync Flow 失败, folderId 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSourceFolderId, folderId);
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
        queryWrapper.eq(SyncFlowEntity::getSyncFlowType, SyncFlowTypeEnum.INTERNAL_TO_CONTENT);
        List<SyncFlowEntity> dbResult = this.baseMapper.selectList(queryWrapper);

        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public SyncFlowEntity getInternal2ContentSyncFlowByFolderId(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("获取 Sync Flow 失败, folderId 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getDestFolderId, folderId);
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
        queryWrapper.eq(SyncFlowEntity::getSyncFlowType, SyncFlowTypeEnum.INTERNAL_TO_CONTENT);
        List<SyncFlowEntity> dbResult = this.baseMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return null;
        }
        if (dbResult.size() > 1) {
            throw new SyncDuoException("internal->content 出现一对多");
        }
        return dbResult.get(0);
    }

    public List<SyncFlowEntity> getBySyncFlowStatus(SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowStatusEnum)) {
            throw new SyncDuoException("syncFlowStatusEnum 为空");
        }
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncStatus, syncFlowStatusEnum.name());
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public List<SyncFlowEntity> getAllSyncFlow() {
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncFlowEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public List<SyncFlowEntity> getBySyncFlowType(SyncFlowTypeEnum syncFlowTypeEnum) {
        LambdaQueryWrapper<SyncFlowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncFlowEntity::getSyncFlowType, syncFlowTypeEnum.name());
        queryWrapper.eq(SyncFlowEntity::getSyncFlowDeleted, DeletedEnum.NOT_DELETED.getCode());
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

    public void deleteSyncFlow(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 检查参数
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new SyncDuoException("syncFlowEntity is null");
        }
        // 删除 sync-flow
        syncFlowEntity.setSyncFlowDeleted(DeletedEnum.DELETED.getCode());
        boolean isDeleted = this.updateById(syncFlowEntity);
        if (!isDeleted) {
            throw new SyncDuoException("删除 sync-flow 失败");
        }
        // 从 event count map 中删除
        this.eventCountMap.remove(syncFlowEntity.getSyncFlowId());
    }
}
