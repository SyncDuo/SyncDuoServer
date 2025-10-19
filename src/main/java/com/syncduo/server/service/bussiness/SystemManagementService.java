package com.syncduo.server.service.bussiness;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.internal.FilesystemEvent;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.service.restic.ResticFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;


@Service
@Slf4j
public class SystemManagementService {

    private final FolderWatcher folderWatcher;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final ResticFacadeService resticFacadeService;

    // SyncFlowId:int, > 0 表示有 reader, < 0 表示有 writer. 支持跨线程释放
    private final Map<Long, AtomicInteger> syncFLowLockMap = new ConcurrentHashMap<>(10);

    @Value("${syncduo.server.system.eventDebounceWindowSec}")
    private long DEBOUNCE_WINDOW;

    @Autowired
    public SystemManagementService(
            FolderWatcher folderWatcher,
            SyncFlowService syncFlowService,
            RcloneFacadeService rcloneFacadeService,
            DebounceService debounceService,
            ResticFacadeService resticFacadeService) {
        this.folderWatcher = folderWatcher;
        this.syncFlowService = syncFlowService;
        this.rcloneFacadeService = rcloneFacadeService;
        this.moduleDebounceService = debounceService.forModule("SystemManagementService");
        this.resticFacadeService = resticFacadeService;
    }

    public void copyFile(FilesystemEvent filesystemEvent) {
        log.debug("system management receive fileEvent: {}", filesystemEvent);
        String sourceFolderPath = filesystemEvent.getFolder().toAbsolutePath().toString();
        String fileName = filesystemEvent.getFile().getFileName().toString();
        // 根据 filesystem event 的 folder 查询下游 syncflow entity
        List<SyncFlowEntity> downstreamSyncFlowEntityList =
                this.syncFlowService.getBySourceFolderPath(sourceFolderPath);
        if (CollectionUtils.isEmpty(downstreamSyncFlowEntityList)) {
            log.info("filesystem event {} handled. there is no syncflow related", filesystemEvent);
            return;
        }
        for (SyncFlowEntity syncFlowEntity : downstreamSyncFlowEntityList) {
            if (SyncFlowStatusEnum.isTransitionProhibit(
                    syncFlowEntity.getSyncStatus(),
                    SyncFlowStatusEnum.COPY_FILE)) {
                // 不允许的 copy file 的状态(FAILED, PAUSE), 则继续保持原样, 等待下一次 rescan
                continue;
            }
            if (this.rcloneFacadeService.isFileFiltered(fileName, syncFlowEntity)) {
                continue;
            }
            this.copyFileAsync(syncFlowEntity, filesystemEvent);
        }
    }

    @Async("generalTaskScheduler")
    protected void copyFileAsync(SyncFlowEntity syncFlowEntity, FilesystemEvent filesystemEvent) {
        // 获取 syncflow entity 的锁
        this.readLock(syncFlowEntity);
        try {
            // syncflow status 修改为 RUNNING
            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.COPY_FILE);
        } catch (Exception ex){
            log.error("copyFileAsync failed. updateSyncFlowStatus failed. syncFlow:{}", syncFlowEntity, ex);
            this.readUnlock(syncFlowEntity);
            return;
        }
        // 发起 copy file 的请求
        log.info("SyncFlowEntity: {} handle FilesystemEvent:{}", syncFlowEntity, filesystemEvent);
        this.rcloneFacadeService.copyFile(syncFlowEntity, filesystemEvent)
                .thenCompose(copyJobEntity -> {
                    this.readUnlock(syncFlowEntity);
                    // copy file 成功后, 发起一个 delay 的 syncflow check, 用于削峰
                    this.moduleDebounceService.debounce(
                            "SyncFlowId::%s::checkStatus".formatted(syncFlowEntity.getSyncFlowId()),
                            () -> this.checkSyncFlowStatusAsync(syncFlowEntity, false),
                            DEBOUNCE_WINDOW
                    );
                    // copy file 成功后, 获取 copy file 的详细数据
                    return this.rcloneFacadeService.updateCopyJobStat(copyJobEntity);
                })
                .exceptionally(ex -> {
                    this.readUnlock(syncFlowEntity);
                    // copy file 失败, 记录日志和数据库
                    log.error("copy file failed. SyncFlowEntity is {}, FilesystemEvent is {}",
                            syncFlowEntity, filesystemEvent, ex);
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.FAILED);
                    return null;
                });
    }

    @Async("generalTaskScheduler")
    public void checkSyncFlowStatusAsync(SyncFlowEntity syncFlowEntity, boolean isInitialScan) {
        // 获取 syncflow 的锁
        this.writeLock(syncFlowEntity);
        log.debug("required lock. syncflow:{}", syncFlowEntity);
        try {
            // 设置 SyncFlowStatus 为 RESCAN or INITIAL_SCAN, 这是一个 fall back 的处理逻辑
            String from = syncFlowEntity.getSyncStatus();
            SyncFlowStatusEnum to = isInitialScan ? SyncFlowStatusEnum.INITIAL_SCAN : SyncFlowStatusEnum.RESCAN;
            if (SyncFlowStatusEnum.isTransitionProhibit(from, to)) {
                this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, to);
            } else {
                this.writeUnlock(syncFlowEntity);
                log.warn("checkSyncFlowStatusAsync failed. syncFlow:{} status {} to {} not allow",
                        syncFlowEntity, from ,to);
                return;
            }
        } catch (Exception ex) {
            log.error("checkSyncFlowStatus failed. updateSyncFlowStatus failed. syncFlow:{}", syncFlowEntity, ex);
            // 释放锁
            this.writeUnlock(syncFlowEntity);
            return;
        }
        // 检查
        this.rcloneFacadeService.oneWayCheck(syncFlowEntity)
                .thenCompose(isSync -> {
                    if (isSync) {
                        // 释放锁
                        this.writeUnlock(syncFlowEntity);
                        // 如果同步则更新状态为 SYNC, 然后退出
                        this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
                        return CompletableFuture.completedFuture(null);
                    }
                    // 不同步则执行 syncCopy
                    return this.rcloneFacadeService.syncCopy(syncFlowEntity)
                            .thenCompose(copyJobEntity -> {
                                // 释放锁
                                this.writeUnlock(syncFlowEntity);
                                // sync copy 成功, 则认为两个文件夹同步
                                this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
                                // 记录 sync copy 的数据
                                return this.rcloneFacadeService.updateCopyJobStat(copyJobEntity);
                            });
                })
                .exceptionally(ex -> {
                    // 释放锁
                    this.writeUnlock(syncFlowEntity);
                    // sync copy 失败, 则认为两个文件夹没有同步
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.FAILED);
                    log.error("checkSyncFlowStatus failed. syncFlow:{}", syncFlowEntity, ex);
                    return null;
                });
    }

    // initial delay 5 minutes, fixDelay 30 minutes. unit is millisecond
    @Scheduled(
            initialDelay = 1000 * 60 * 5,
            fixedDelayString = "${syncduo.server.system.checkSyncflowStatusIntervalMillis}",
            scheduler = "systemManagementTaskScheduler"
    )
    public void rescanAllSyncFlow() {
        log.info("Rescan All SyncFlow");
        // 获取全部 syncflow
        List<SyncFlowEntity> syncFlowEntityList = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(syncFlowEntityList)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
            // check status flow valid
            if (SyncFlowStatusEnum.isTransitionProhibit(
                    syncFlowEntity.getSyncStatus(),
                    SyncFlowStatusEnum.RESCAN)
            ) {
                continue;
            }
            try {
                // 建立 watcher, 并发起 scan
                this.folderWatcher.addWatcher(syncFlowEntity.getSourceFolderPath());
                this.checkSyncFlowStatusAsync(syncFlowEntity, false);
            } catch (Exception e) {
                log.error("rescanAllSyncFlow has error. initialScan failed. sync flow is {}", syncFlowEntity,
                        new BusinessException("systemStartUp has error", e));
            }
        }
    }

    // initial delay 5 minutes, fixDelay 4 hours. unit is millisecond
    @Scheduled(
            initialDelay = 1000 * 60 * 5,
            fixedDelayString = "${syncduo.server.system.backupIntervalMillis}",
            scheduler = "systemManagementTaskScheduler"
    )
    protected void periodicalBackup() {
        log.info("Periodical Backup SyncFlow");
        // 获取全部 syncflow
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            try {
                this.resticFacadeService.backup(syncFlowEntity);
            } catch (BusinessException e) {
                log.error("periodicalBackup failed. syncFlowEntity is {}", syncFlowEntity, e);
            }
        }
    }

    private void readLock(SyncFlowEntity syncFlowEntity) {
        int waitCount = 0;
        while (true) {
            AtomicInteger counter = this.getCounter(syncFlowEntity);
            if (counter.get() >= 0) {
                counter.incrementAndGet();
                return;
            } else {
                // read lock 大于 15 分钟
                if (waitCount >= 60) {
                    log.warn("readLock acquire exceed 15 minutes");
                }
                LockSupport.parkNanos(15L * 1000000000); // 等待 15 秒后再获取
                waitCount++;
            }
        }
    }

    private void readUnlock(SyncFlowEntity syncFlowEntity) {
        this.getCounter(syncFlowEntity).decrementAndGet();
    }

    private void writeLock(SyncFlowEntity syncFlowEntity) {
        int waitCount = 0;
        while (true) {
            AtomicInteger counter = this.getCounter(syncFlowEntity);
            if (counter.get() == 0) {
                counter.set(-1);
                return;
            } else {
                // write lock 大于 15 分钟
                if (waitCount >= 180) {
                    log.warn("readLock acquire exceed 15 minutes");
                }
                LockSupport.parkNanos(5L * 1000000000); // 等待 5 秒后再获取
                waitCount++;
            }
        }
    }

    private void writeUnlock(SyncFlowEntity syncFlowEntity) {
        this.getCounter(syncFlowEntity).set(0);
    }

    private AtomicInteger getCounter(SyncFlowEntity syncFlowEntity) {
        Long syncFlowId = syncFlowEntity.getSyncFlowId();
        this.syncFLowLockMap.putIfAbsent(syncFlowId, new AtomicInteger(0));
        return this.syncFLowLockMap.get(syncFlowId);
    }
}
