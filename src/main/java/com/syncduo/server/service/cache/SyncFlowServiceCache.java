package com.syncduo.server.service.cache;

import com.sun.jna.platform.win32.Winspool;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.bussiness.impl.SyncFlowService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SyncFlowServiceCache extends SyncFlowService {

    // <folderId, syncFlowStatus>
    // 其中 syncFlowStatus 的 source folder id = map 的 key(folderId)
    private final Map<Long, List<SyncFlowStatus>> cacheMap = new ConcurrentHashMap<>();

    public void preloadCache() {
        // 幂等
        this.cacheMap.clear();
        // 查询 DB
        List<SyncFlowEntity> allSyncFlowDB = super.getAllSyncFlowDB();
        if (CollectionUtils.isEmpty(allSyncFlowDB)) {
            return;
        }
        addEntry(allSyncFlowDB);
    }

    public SyncFlowEntity getBySyncFlowId(Long syncFlowId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowId)) {
            throw new SyncDuoException("syncFlowId is null");
        }
        // 遍历缓存查询
        Set<Map.Entry<Long, List<SyncFlowStatus>>> entries = this.cacheMap.entrySet();
        for (Map.Entry<Long, List<SyncFlowStatus>> entry : entries) {
            List<SyncFlowStatus> syncFlowStatusList = entry.getValue();
            for (SyncFlowStatus syncFlowStatus : syncFlowStatusList) {
                if (syncFlowId.equals(syncFlowStatus.getSyncFlowEntity().getSyncFlowId())) {
                    return syncFlowStatus.getSyncFlowEntity();
                }
            }
        }
        // 缓存没有命中, 查询数据库
        // 该方法不存在混合策略更新缓存, syncFlowId 是最精确的查找
        SyncFlowEntity dbResult = super.getBySyncFlowIdDB(syncFlowId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return null;
        }
        this.addEntry(Collections.singletonList(dbResult));
        return dbResult;
    }

    public SyncFlowEntity createSyncFlow(
            Long sourceFolderId,
            Long destFolderId,
            SyncFlowTypeEnum syncFlowType,
            String syncFlowName) throws SyncDuoException {
        List<SyncFlowStatus> cacheResult = this.cacheMap.get(sourceFolderId);
        // 没有缓存, 说明是第一次创建
        if (CollectionUtils.isEmpty(cacheResult)) {
            // 更新数据库
            SyncFlowEntity syncFlowEntity = super.createSyncFlowDB(
                    sourceFolderId,
                    destFolderId,
                    syncFlowType,
                    syncFlowName);
            // 更新缓存
            this.addEntry(Collections.singletonList(syncFlowEntity));
            return syncFlowEntity;
        }
        // 有缓存, 则查找
        for (SyncFlowStatus syncFlowStatus : cacheResult) {
            if (syncFlowStatus.getSyncFlowEntity().getDestFolderId().equals(destFolderId)) {
                return syncFlowStatus.getSyncFlowEntity();
            }
        }
        // 没有命中, 则说明是第一次创建
        // 更新数据库
        SyncFlowEntity syncFlowEntity = super.createSyncFlowDB(
                sourceFolderId,
                destFolderId,
                syncFlowType,
                syncFlowName);
        // 更新缓存
        this.addEntry(Collections.singletonList(syncFlowEntity));
        return syncFlowEntity;
    }

    public SyncFlowEntity getBySourceFolderIdAndDest(
            long sourceFolderId,
            long destFolderId)
            throws SyncDuoException {
        List<SyncFlowStatus> cacheResult = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(cacheResult)) {
            this.hybridUpdateCache(
                    sourceFolderId,
                    () -> Collections.singletonList(super.getBySourceFolderIdAndDestDB(sourceFolderId, destFolderId))
            );
            cacheResult = this.cacheMap.get(sourceFolderId);
            if (CollectionUtils.isEmpty(cacheResult)) {
                return null;
            }
        }
        for (SyncFlowStatus syncFlowStatus : cacheResult) {
            if (syncFlowStatus.getSyncFlowEntity().getDestFolderId().equals(destFolderId)) {
                return syncFlowStatus.getSyncFlowEntity();
            }
        }
        return null;
    }

    public List<SyncFlowEntity> getBySourceFolderId(long sourceFolderId) throws SyncDuoException {
        List<SyncFlowStatus> cacheResult = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(cacheResult)) {
            // 混合策略更新缓存
            this.hybridUpdateCache(
                    sourceFolderId,
                    () -> super.getBySourceFolderIdDB(sourceFolderId)
            );
            // 重复查询
            cacheResult = this.cacheMap.get(sourceFolderId);
            // 仍然为空, 则返回空列表
            if (CollectionUtils.isEmpty(cacheResult)) {
                return Collections.emptyList();
            }
        }
        return this.syncFlowStatus2SyncFlowList(cacheResult);
    }

    public List<SyncFlowEntity> getAllSyncFlow() {
        List<SyncFlowStatus> syncFlowStatusList = new ArrayList<>(20);
        Set<Map.Entry<Long, List<SyncFlowStatus>>> entries = this.cacheMap.entrySet();
        for (Map.Entry<Long, List<SyncFlowStatus>> entry : entries) {
            syncFlowStatusList.addAll(entry.getValue());
        }
        return this.syncFlowStatus2SyncFlowList(syncFlowStatusList);
    }

    public void updateSyncFlowStatus(
            SyncFlowEntity syncFlowEntity,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        // 更新数据库
        SyncFlowEntity dbResult = super.updateSyncFlowStatusDB(syncFlowEntity, syncFlowStatusEnum);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        // 更新缓存
        this.hybridUpdateCache(
                dbResult.getSourceFolderId(),
                () -> Collections.singletonList(dbResult)
        );
    }

    public void updateSyncFlowStatus(
            Long syncFlowId,
            SyncFlowStatusEnum syncFlowStatusEnum) throws SyncDuoException {
        // 更新数据库
        SyncFlowEntity dbResult = super.updateSyncFlowStatusDB(syncFlowId, syncFlowStatusEnum);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        // 更新缓存
        this.hybridUpdateCache(
                dbResult.getSourceFolderId(),
                () -> Collections.singletonList(dbResult)
        );
    }

    public void deleteSyncFlow(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 更新数据库
        super.deleteSyncFlowDB(syncFlowEntity);
        // 删除缓存
        this.removeElementInEntry(syncFlowEntity);
    }

    // sourceFolderId 对应的全部 sync flow 都增加 pending event count
    public void addPendingEventCount(long sourceFolderId) throws SyncDuoException {
        List<SyncFlowStatus> cacheResult = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(cacheResult)) {
            // 混合策略更新缓存
            this.hybridUpdateCache(
                    sourceFolderId,
                    () -> super.getBySourceFolderIdDB(sourceFolderId)
            );
            // 重复查询, 仍然为空则说明 syncFlow 被删除
            cacheResult = this.cacheMap.get(sourceFolderId);
            if (CollectionUtils.isEmpty(cacheResult)) {
                return;
            }
        }
        for (SyncFlowStatus syncFlowStatus : cacheResult) {
            boolean statusChanged = syncFlowStatus.addPendingEventCount();
            if (statusChanged) {
                this.updateSyncFlowStatus(
                        syncFlowStatus.getSyncFlowEntity(),
                        SyncFlowStatusEnum.NOT_SYNC
                );
            }
        }
    }

    // sourceFolderId 对应的全部 sync flow 都减少 pending event count
    public void decrPendingEventCount(Long sourceFolderId) throws SyncDuoException {
        List<SyncFlowStatus> cacheResult = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(cacheResult)) {
            return;
        }
        // 找到 folder id 为 source id, 对应的 syncFlowId
        for (SyncFlowStatus syncFlowStatus : cacheResult) {
            boolean isStatusChanged = syncFlowStatus.decrPendingEventCount();
            if (isStatusChanged) {
                this.updateSyncFlowStatus(
                        syncFlowStatus.getSyncFlowEntity(),
                        SyncFlowStatusEnum.SYNC
                );
            }
        }
    }

    // 指定 sync flow id 增加 pending event count
    public void addPendingEventCount(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        SyncFlowStatus cacheResult = this.getSyncFlowStatusBySyncFlowEntity(syncFlowEntity);
        if (ObjectUtils.isEmpty(cacheResult)) {
            // 混合策略更新缓存
            this.hybridUpdateCache(
                    syncFlowEntity.getSourceFolderId(),
                    () -> {
                        SyncFlowEntity dbResult = super.getBySyncFlowIdDB(syncFlowEntity.getSyncFlowId());
                        return Collections.singletonList(dbResult);
                    }
            );
            // 重复查询
            cacheResult = this.getSyncFlowStatusBySyncFlowEntity(syncFlowEntity);
            // 仍然为空, 则说明 syncFlow 已删除
            if (ObjectUtils.isEmpty(cacheResult)) {
                return;
            }
        }
        boolean isStatusChanged = cacheResult.addPendingEventCount();
        if (isStatusChanged) {
            this.updateSyncFlowStatus(
                    cacheResult.getSyncFlowEntity(),
                    SyncFlowStatusEnum.NOT_SYNC
            );
        }
    }

    // 指定 sync flow id 减少 pending event count
    public void decrPendingEventCount(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        SyncFlowStatus cacheResult = this.getSyncFlowStatusBySyncFlowEntity(syncFlowEntity);
        // 缓存为空, 说明已删除
        if (ObjectUtils.isEmpty(cacheResult)) {
            return;
        }
        boolean isStatusChanged = cacheResult.decrPendingEventCount();
        if (isStatusChanged) {
            this.updateSyncFlowStatus(cacheResult.getSyncFlowEntity(), SyncFlowStatusEnum.SYNC);
        }
    }

    private SyncFlowStatus getSyncFlowStatusBySyncFlowEntity(SyncFlowEntity syncFlowEntity) {
        Long sourceFolderId = syncFlowEntity.getSourceFolderId();
        Long syncFlowId = syncFlowEntity.getSyncFlowId();
        List<SyncFlowStatus> syncFlowStatusList = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            return null;
        }
        for (SyncFlowStatus syncFlowStatus : syncFlowStatusList) {
            if (syncFlowStatus.getSyncFlowEntity().getSyncFlowId().equals(syncFlowId)) {
                return syncFlowStatus;
            }
        }
        return null;
    }

    private void hybridUpdateCache(long sourceFolderId, FetchSyncFlow fetchSyncFlow) throws SyncDuoException {
        // 如果 sourceFolderId cache map 没有 entry, 则从DB获取, 并创建 entry
        List<SyncFlowStatus> syncFlowStatusList = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            List<SyncFlowEntity> dbResult = super.getBySourceFolderIdDB(sourceFolderId);
            if (CollectionUtils.isEmpty(dbResult)) {
                return;
            }
            this.addEntry(dbResult);
            return;
        }
        // 如果 fetchSyncFlow 为 null, 说明不需要细粒度地从数据库更新缓存
        if (ObjectUtils.isEmpty(fetchSyncFlow)) {
            return;
        }
        // 如果 sourceFolderId cache map 有 entry, 则调用 fetchSyncFlow, 并更新 entry
        List<SyncFlowEntity> syncFlowEntities = fetchSyncFlow.get();
        if (CollectionUtils.isEmpty(syncFlowEntities)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntities) {
            this.addOrUpdateElementInEntry(syncFlowStatusList, syncFlowEntity);
        }
        this.cacheMap.put(sourceFolderId, syncFlowStatusList);
    }

    private void addEntry(List<SyncFlowEntity> syncFlowEntityList) {
        if (CollectionUtils.isEmpty(syncFlowEntityList)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
            Long sourceFolderId = syncFlowEntity.getSourceFolderId();
            if (ObjectUtils.isEmpty(sourceFolderId)) {
                continue;
            }
            List<SyncFlowStatus> syncFlowStatusList = this.cacheMap.get(sourceFolderId);
            // entry 为空, 则新建 entry 并添加 element
            if (CollectionUtils.isEmpty(syncFlowStatusList)) {
                syncFlowStatusList = new ArrayList<>(1);
                syncFlowStatusList.add(new SyncFlowStatus(syncFlowEntity));
            } else {
                // entry 不为空, 则添加 element 或 更新
                this.addOrUpdateElementInEntry(syncFlowStatusList, syncFlowEntity);
            }
            this.cacheMap.put(sourceFolderId, syncFlowStatusList);
        }
    }

    private void addOrUpdateElementInEntry(
            List<SyncFlowStatus> syncFlowStatusList,
            SyncFlowEntity syncFlowEntity) {
        if (CollectionUtils.isEmpty(syncFlowStatusList) || ObjectUtils.isEmpty(syncFlowEntity)) {
            return;
        }
        boolean updated = false;
        for (SyncFlowStatus syncFlowStatus : syncFlowStatusList) {
            if (syncFlowStatus.getSyncFlowEntity().getSyncFlowId().equals(syncFlowEntity.getSyncFlowId())) {
                syncFlowStatus.setSyncFlowEntity(syncFlowEntity);
                updated = true;
            }
        }
        if (!updated) {
            syncFlowStatusList.add(new SyncFlowStatus(syncFlowEntity));
        }
    }

    private void removeElementInEntry(SyncFlowEntity syncFlowEntity) {
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            return;
        }
        Long sourceFolderId = syncFlowEntity.getSourceFolderId();
        Long syncFlowId = syncFlowEntity.getSyncFlowId();
        List<SyncFlowStatus> cacheResult = this.cacheMap.get(sourceFolderId);
        if (CollectionUtils.isEmpty(cacheResult)) {
            return;
        }
        cacheResult.removeIf(syncFlowStatus ->
                syncFlowStatus.getSyncFlowEntity().getSyncFlowId().equals(syncFlowId));
    }

    private List<SyncFlowEntity> syncFlowStatus2SyncFlowList(List<SyncFlowStatus> syncFlowStatusList) {
        if (CollectionUtils.isEmpty(syncFlowStatusList)) {
            return Collections.emptyList();
        }
        return syncFlowStatusList.stream().map(SyncFlowStatus::getSyncFlowEntity).toList();
    }

    @Data
    static class SyncFlowStatus {
        private SyncFlowEntity syncFlowEntity;

        private AtomicInteger pendingEventCount;

        public SyncFlowStatus(SyncFlowEntity syncFlowEntity) {
            this.syncFlowEntity = syncFlowEntity;
            pendingEventCount = new AtomicInteger(0);
        }

        // 如果 pending event count 从 0->1 跳变, 则返回 true, 否则返回 false
        public boolean addPendingEventCount() {
            return this.pendingEventCount.addAndGet(1) == 1;
        }

        // 如果 pending event count 从 1->0 跳变, 则返回 true, 否则返回 false
        public boolean decrPendingEventCount() {
            return this.pendingEventCount.decrementAndGet() == 0;
        }
    }

    @FunctionalInterface
    private interface FetchSyncFlow {
        List<SyncFlowEntity> get() throws SyncDuoException;
    }
}
