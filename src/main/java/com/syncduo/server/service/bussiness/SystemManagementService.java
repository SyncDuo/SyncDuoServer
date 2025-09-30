package com.syncduo.server.service.bussiness;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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
                // source folder add watcher
                this.folderWatcher.addWatcher(syncFlowEntity.getSourceFolderPath());
                // check status
                this.checkSyncFlowStatus(syncFlowEntity);
            } catch (Exception e) {
                log.error("systemStartUp has error. " +
                        "sync flow is {}", syncFlowEntity, new BusinessException("systemStartUp has error", e));
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
            // filter out only sync syncflow
            if (!SyncFlowStatusEnum.SYNC.name().equals(syncFlowEntity.getSyncStatus())) {
                continue;
            }
            try {
                this.checkSyncFlowStatus(syncFlowEntity);
            } catch (Exception e) {
                log.error("periodicalScan failed. " +
                        "sync flow is {}", syncFlowEntity, new BusinessException("periodicalScan failed.", e));
            }
        }
    }

    @Async("generalTaskScheduler")
    public void checkSyncFlowStatus(SyncFlowEntity syncFlowEntity) {
        // 设置为 RUNNING
        this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.RUNNING);
        // 检查并更新状态
        boolean isSync = rcloneFacadeService.oneWayCheck(syncFlowEntity);
        if (isSync) {
            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
        } else {
            // syncflow 不同步, 发起 sync copy
            this.rcloneFacadeService.syncCopy(syncFlowEntity);
        }
    }
}
