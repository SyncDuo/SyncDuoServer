package com.syncduo.server.bus;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.internal.DownStreamEvent;
import com.syncduo.server.model.internal.FileSystemEvent;
import com.syncduo.server.service.cache.SyncFlowServiceCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// todo: 改造为 one queue for sync flow, sync flow 的状态更新在这里调用. event 的持久化, pause event 的处理都放在这里
@Component
@Slf4j
public class SystemBus {

    private final SyncFlowServiceCache syncFlowServiceCache;

    private final ConcurrentLinkedQueue<FileSystemEvent> fileSystemEventQueue = new ConcurrentLinkedQueue<>();

    // <syncFlowId, queue>
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<DownStreamEvent>> downStreamEventQueueMap =
            new ConcurrentHashMap<>();

    @Autowired
    public SystemBus(SyncFlowServiceCache syncFlowServiceCache) {
        this.syncFlowServiceCache = syncFlowServiceCache;
    }

    public void sendFileEvent(FileSystemEvent fileSystemEvent) throws SyncDuoException {
        log.debug("fileEvent: {}", fileSystemEvent);
        this.fileSystemEventQueue.offer(fileSystemEvent);
    }

    public FileSystemEvent getFileEvent() {
        return this.fileSystemEventQueue.poll();
    }

    public void sendDownStreamEvent(DownStreamEvent downStreamEvent) throws SyncDuoException {
        log.debug("downStreamEvent: {}", downStreamEvent);
        // todo: 持久化事件

        // 如果 syncFlowEntity 为空, 则填充 syncFlowEntity 并放入对应的 queue
        SyncFlowEntity syncFlowEntity = downStreamEvent.getSyncFlowEntity();
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            List<SyncFlowEntity> syncFlowEntityList =
                    this.syncFlowServiceCache.getBySourceFolderId(downStreamEvent.getFolderEntity().getFolderId());
            for (SyncFlowEntity syncFlowEntity1 : syncFlowEntityList) {
                DownStreamEvent copyDownStreamEvent = new DownStreamEvent(downStreamEvent);
                copyDownStreamEvent.setSyncFlowEntity(syncFlowEntity1);
                this.addDownStreamEvent2OneSyncFlow(copyDownStreamEvent);
            }
            return;
        }
        // 如果不为空, 则直接放入对应的 queue
        this.addDownStreamEvent2OneSyncFlow(downStreamEvent);
    }

    private void addDownStreamEvent2OneSyncFlow(DownStreamEvent downStreamEvent) {
        SyncFlowEntity syncFlowEntity = downStreamEvent.getSyncFlowEntity();
        Long syncFlowId = syncFlowEntity.getSyncFlowId();
        // 对应 key 没有初始化 queue, 或者 key 没有初始化, 则初始化并放入 event
        ConcurrentLinkedQueue<DownStreamEvent> queue = this.downStreamEventQueueMap.get(syncFlowId);
        if (ObjectUtils.isEmpty(queue)) {
            queue = new ConcurrentLinkedQueue<>();
        }
        queue.add(downStreamEvent);
        this.downStreamEventQueueMap.put(syncFlowId, queue);
    }

    public DownStreamEvent getDownStreamEvent() throws SyncDuoException {
        Set<Map.Entry<Long, ConcurrentLinkedQueue<DownStreamEvent>>> entries =
                this.downStreamEventQueueMap.entrySet();
        for (Map.Entry<Long, ConcurrentLinkedQueue<DownStreamEvent>> entry : entries) {
            // queue 或者 queue 没有 event, 则忽略
            ConcurrentLinkedQueue<DownStreamEvent> queue = entry.getValue();
            if (ObjectUtils.anyNull(queue, queue.peek())) {
                continue;
            }
            Long syncFlowId = entry.getKey();
            // sync flow 暂停, 则不返回 event
            SyncFlowEntity syncFlowEntity = this.syncFlowServiceCache.getBySyncFlowId(syncFlowId);
            if (SyncFlowStatusEnum.PAUSE.name().equals(syncFlowEntity.getSyncStatus())) {
                continue;
            }
            return queue.poll();
        }
        return null;
    }

    public void clearQueueBySyncFlowId(long syncFlowId) {
        ConcurrentLinkedQueue<DownStreamEvent> queue = this.downStreamEventQueueMap.get(syncFlowId);
        if (ObjectUtils.isEmpty(queue)) {
            queue = new ConcurrentLinkedQueue<>();
        }
        queue.clear();
        this.downStreamEventQueueMap.put(syncFlowId, queue);
    }
}
