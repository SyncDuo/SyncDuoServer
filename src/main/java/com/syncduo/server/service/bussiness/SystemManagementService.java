package com.syncduo.server.service.bussiness;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.db.impl.*;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class SystemManagementService {

    private final FolderWatcher folderWatcher;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    @Autowired
    public SystemManagementService(
            FolderWatcher folderWatcher,
            SyncFlowService syncFlowService,
            RcloneFacadeService rcloneFacadeService) {
        this.folderWatcher = folderWatcher;
        this.syncFlowService = syncFlowService;
        this.rcloneFacadeService = rcloneFacadeService;
    }

    public void checkAllSyncFlowStatus() {
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
                // check
                boolean isSync = rcloneFacadeService.oneWayCheck(syncFlowEntity);
                if (isSync) {
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
                    continue;
                }
                // update status
                this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.RUNNING);
                // copy
                this.rcloneFacadeService.syncCopy(syncFlowEntity);
                // source folder add watcher
                this.folderWatcher.addWatcher(syncFlowEntity.getSourceFolderPath());
            } catch (SyncDuoException e) {
                log.error("systemStartUp has error.", e);
            }
        }
    }

    // initial delay 5 minutes, fixDelay 30 minutes. unit is millisecond
    @Scheduled(
            initialDelay = 1000 * 60 * 5,
            fixedDelayString = "${syncduo.server.system.checkSyncflowStatusIntervalMillis:1800000}",
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
            // filter only sync syncflow
            if (!SyncFlowStatusEnum.SYNC.name().equals(syncFlowEntity.getSyncStatus())) {
                continue;
            }
            try {
                // check
                boolean isSync = rcloneFacadeService.oneWayCheck(syncFlowEntity);
                if (!isSync) {
                    // update status
                    this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.RUNNING);
                    // copy
                    this.rcloneFacadeService.syncCopy(syncFlowEntity);
                }
            } catch (SyncDuoException e) {
                log.error("periodicalScan failed.", e);
            }
        }
    }
}
