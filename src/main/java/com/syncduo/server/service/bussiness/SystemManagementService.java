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
import java.util.concurrent.CompletableFuture;


@Service
@Slf4j
public class SystemManagementService {

    private final FolderWatcher folderWatcher;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final ResticFacadeService resticFacadeService;

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
        String sourceFolderPath = filesystemEvent.getFolder().toAbsolutePath().toString();
        String fileName = filesystemEvent.getFile().getFileName().toString();
        // 根据 filesystem event 的 folder 查询下游 syncflow entity, 过滤 PAUSE 的记录
        List<SyncFlowEntity> downstreamSyncFlowEntityList =
                this.syncFlowService.getBySourceFolderPath(sourceFolderPath, true);
        if (CollectionUtils.isEmpty(downstreamSyncFlowEntityList)) {
            log.info("filesystem event {} handled. there is no syncflow related", filesystemEvent);
            return;
        }
        for (SyncFlowEntity syncFlowEntity : downstreamSyncFlowEntityList) {
            // 手动 filter, 因为 rclone 设计 copyfile api 不支持 filter
            if (this.rcloneFacadeService.isFileFiltered(fileName, syncFlowEntity)) {
                continue;
            }
            // syncflow status 修改为 RUNNING
            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.RUNNING);
            // 发起 copy file 的请求
            log.info("SyncFlowEntity: {} handle FilesystemEvent:{}", syncFlowEntity, filesystemEvent);
            this.rcloneFacadeService.copyFile(syncFlowEntity, filesystemEvent)
                    // copy file 成功后, 获取 copy file 的详细数据
                    .thenCompose(this.rcloneFacadeService::updateCopyJobStat)
                    // 获取详细数据之后, 发起 debounce 的 checkSyncFlowStatus
                    .thenRun(() -> this.moduleDebounceService.debounce(
                            "SyncFlowId::%s".formatted(syncFlowEntity.getSyncFlowId()),
                            () -> this.checkSyncFlowStatus(syncFlowEntity, 2),
                            DEBOUNCE_WINDOW
                    ))
                    .exceptionally(ex -> {
                        log.error("copy file error. SyncFlowEntity is {}, FilesystemEvent is {}",
                                syncFlowEntity, filesystemEvent, ex);
                        try {
                            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.FAILED);
                        } catch (Exception e) {
                            log.error("copy file error. updateSyncFlowStatus failed.", e);
                        }
                        return null;
                    });
        }
    }

    @Async("generalTaskScheduler")
    public void checkSyncFlowStatus(SyncFlowEntity syncFlowEntity, int retryCheckCount) {
        if (retryCheckCount <= 0) {
            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.FAILED);
            log.error("checkSyncFlowStatus failed. exceed max retry.");
            return;
        }
        try {
            // 设置为 RUNNING
            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.RUNNING);
            // 检查
            this.rcloneFacadeService.oneWayCheck(syncFlowEntity)
                    .thenCompose(isSync -> {
                        // 如果同步则更新状态为 SYNC, 然后退出
                        if (isSync) {
                            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
                            return CompletableFuture.completedFuture(null);
                        } else {
                            return this.rcloneFacadeService.syncCopy(syncFlowEntity)
                                    .thenCompose(this.rcloneFacadeService::updateCopyJobStat)
                                    .thenRun(() -> this.checkSyncFlowStatus(
                                            syncFlowEntity,
                                            retryCheckCount - 1));
                        }
                    })
                    .exceptionally(ex -> {
                        this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.FAILED);
                        log.error("checkSyncFlowStatus failed. SyncFlowEntity is {}", syncFlowEntity, ex);
                        return null;
                    });
        } catch (Exception e) {
            log.warn("checkSyncFlowStatus failed. SyncFlowEntity is {}", syncFlowEntity, e);
        }
    }

    public void checkAllSyncFlowStatus() {
        log.info("System start, check all syncflow status");
        // 获取全部 syncflow
        List<SyncFlowEntity> syncFlowEntityList = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(syncFlowEntityList)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
            // filter pause syncflow
            if (SyncFlowStatusEnum.PAUSE.name().equals(syncFlowEntity.getSyncStatus())) {
                continue;
            }
            try {
                // source folder add watcher
                this.folderWatcher.addWatcher(syncFlowEntity.getSourceFolderPath());
                // check status
                this.checkSyncFlowStatus(syncFlowEntity, 2);
            } catch (Exception e) {
                log.error("systemStartUp has error. " +
                        "sync flow is {}", syncFlowEntity, new BusinessException("systemStartUp has error", e));
            }
        }
    }

    // initial delay 5 minutes, fixDelay 30 minutes. unit is millisecond
    @Scheduled(
            initialDelay = 1000 * 60 * 5,
            fixedDelayString = "${syncduo.server.system.checkSyncflowStatusIntervalMillis}",
            scheduler = "systemManagementTaskScheduler"
    )
    private void periodicalCheckSyncFlowStatus() {
        log.info("Periodical Check SyncFlow Status");
        // 获取全部 syncflow
        List<SyncFlowEntity> syncFlowEntityList = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(syncFlowEntityList)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
            // filter out only sync syncflow
            if (!SyncFlowStatusEnum.SYNC.name().equals(syncFlowEntity.getSyncStatus())) {
                continue;
            }
            try {
                this.checkSyncFlowStatus(syncFlowEntity, 2);
            } catch (Exception e) {
                log.error("periodicalScan failed. " +
                        "sync flow is {}", syncFlowEntity, new BusinessException("periodicalScan failed.", e));
            }
        }
    }

    // initial delay 5 minutes, fixDelay 4 hours. unit is millisecond
    @Scheduled(
            initialDelay = 1000 * 60 * 5,
            fixedDelayString = "${syncduo.server.system.backupIntervalMillis}",
            scheduler = "systemManagementTaskScheduler"
    )
    private void periodicalBackup() {
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
}
