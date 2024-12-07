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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SyncFlowService
        extends ServiceImpl<SyncFlowMapper, SyncFlowEntity>
        implements ISyncFlowService {

    // map<source-folder-id, List<SyncFlowEventCount>
    // 如果是 source -> internal sync-flow, 则 List 只有一个元素
    // 如果是 internal -> content sync-flow, 则 list 有很多元素
    private final ConcurrentHashMap<Long, List<EventCount>> eventCountMap =
            new ConcurrentHashMap<>(100);

    public void incrSource2InternalCount(Long rootFolderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(rootFolderId)) {
            throw new SyncDuoException("rootFolderId is null");
        }
        List<EventCount> eventCounts = this.eventCountMap.get(rootFolderId);
        // 初始化
        if (CollectionUtils.isEmpty(eventCounts)) {
            SyncFlowEntity source2InternalSyncFlow = this.getSourceSyncFlowByFolderId(rootFolderId);
            eventCounts = new ArrayList<>(1);
            eventCounts.add(new EventCount(source2InternalSyncFlow.getDestFolderId(), 1L));
        } else {
            if (eventCounts.size() > 1) {
                throw new SyncDuoException("source2 internal sync-flow exceed 1");
            }
            EventCount internalEventCount = eventCounts.get(0);
            internalEventCount.setEventCount(internalEventCount.getEventCount() + 1);
        }
        this.eventCountMap.put(rootFolderId, eventCounts);
        log.info("Map info {}", eventCountMap);
    }

    public void decrSource2InternalCount(Long rootFolderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(rootFolderId)) {
            throw new SyncDuoException("rootFolderId is null");
        }
        List<EventCount> eventCounts = this.eventCountMap.get(rootFolderId);
        if (CollectionUtils.isEmpty(eventCounts)) {
            throw new SyncDuoException("can't find source2 internal sync-flow");
        }
        if (eventCounts.size() > 1) {
            throw new SyncDuoException("source2 internal sync-flow exceed 1");
        }
        EventCount internalEventCount = eventCounts.get(0);
        Long eventCount = internalEventCount.getEventCount();
        if (eventCount == 0) {
            throw new SyncDuoException("internal 2 content sync-flow event count already 0");
        }
        if (eventCount > 1) {
            internalEventCount.setEventCount(eventCount - 1);
            return;
        }
        if (eventCount == 1) {
            internalEventCount.setEventCount(0L);
            SyncFlowEntity source2InternalSyncFlow = this.getSourceSyncFlowByFolderId(rootFolderId);
            this.updateSyncFlowStatus(source2InternalSyncFlow, SyncFlowStatusEnum.SYNC);
        }
        log.info("Map info {}", eventCountMap);
    }

    public void incrInternal2ContentCount(Long rootFolderId) throws SyncDuoException {
        // 检查参数
        if (ObjectUtils.anyNull(rootFolderId)) {
            throw new SyncDuoException("rootFolderId 空值");
        }
        // 判断有没有初始化
        List<EventCount> eventCounts = this.eventCountMap.get(rootFolderId);
        if (CollectionUtils.isEmpty(eventCounts)) {
            eventCounts = new ArrayList<>(1);
            // 初始化
            List<SyncFlowEntity> internalSyncFlowList = this.getInternalSyncFlowByFolderId(rootFolderId);
            for (SyncFlowEntity internalSyncFlow : internalSyncFlowList) {
                eventCounts.add(new EventCount(internalSyncFlow.getDestFolderId(), 1L));
            }
            this.eventCountMap.put(rootFolderId, eventCounts);
        } else {
            // 已经初始化了, 则每个 eventCount 增加一个事件计数
            for (EventCount eventCount : eventCounts) {
                eventCount.setEventCount(eventCount.getEventCount() + 1);
            }
        }
        log.info("Map info {}", eventCountMap);
    }

    public void decrInternal2ContentCount(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowEntity)) {
            throw new SyncDuoException("syncFlowEntity is null");
        }
        List<EventCount> eventCounts = this.eventCountMap.get(syncFlowEntity.getSourceFolderId());
        if (CollectionUtils.isEmpty(eventCounts)) {
            throw new SyncDuoException("can't find internal 2 content sync-flow " + syncFlowEntity);
        }
        boolean isDecrease = false;
        boolean isSync = false;
        for (EventCount eventCount : eventCounts) {
            if (eventCount.getDestFolderId().equals(syncFlowEntity.getDestFolderId())) {
                eventCount.setEventCount(eventCount.getEventCount() - 1);
                isDecrease = true;
                if (eventCount.getEventCount() == 0) {
                    isSync = true;
                }
            }
        }
        if(!isDecrease) {
            throw new SyncDuoException("can't find internal 2 content sync-flow " + syncFlowEntity);
        }
        if (isSync) {
            this.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
        }
        log.info("Map info {}", eventCountMap);
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
            boolean saved = this.save(dbResult);
            if (!saved) {
                throw new SyncDuoException("创建 Sync Flow 失败, 无法保存到数据库");
            }
        }
        // source -> internal 添加到 eventCountMap
        List<EventCount> eventCounts = this.eventCountMap.get(dbResult.getSourceFolderId());
        if (CollectionUtils.isEmpty(eventCounts)) {
            eventCounts = new ArrayList<>(1);
            eventCounts.add(new EventCount(dbResult.getDestFolderId(), 0L));
            this.eventCountMap.put(dbResult.getSourceFolderId(), eventCounts);
        }
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

    public SyncFlowEntity getSourceSyncFlowByFolderId(Long folderId) throws SyncDuoException {
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

    public List<SyncFlowEntity> getInternalSyncFlowByFolderId(Long folderId) throws SyncDuoException {
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
        boolean isDeleted = this.removeById(syncFlowEntity);
        if (!isDeleted) {
            throw new SyncDuoException("删除 sync-flow 失败");
        }
        // 从 event count map 中删除
        String syncFlowType = syncFlowEntity.getSyncFlowType();
        try {
            SyncFlowTypeEnum syncFlowTypeEnum = SyncFlowTypeEnum.valueOf(syncFlowType);
            switch (syncFlowTypeEnum) {
                case SOURCE_TO_INTERNAL: this.eventCountMap.remove(syncFlowEntity.getSourceFolderId());
                case INTERNAL_TO_CONTENT: {
                    List<EventCount> eventCounts = this.eventCountMap.get(syncFlowEntity.getSourceFolderId());
                    eventCounts.removeIf(eventCount ->
                            eventCount.getDestFolderId().equals(syncFlowEntity.getDestFolderId()));
                }
            }
        } catch (IllegalArgumentException e) {
            throw new SyncDuoException("syncFlowTypeEnum not support " + syncFlowType);
        }
    }

    @AllArgsConstructor
    @Data
    private static class EventCount {

        Long destFolderId;

        Long eventCount;
    }
}
