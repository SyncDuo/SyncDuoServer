package com.syncduo.server.service.restic;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.model.restic.backup.Error;
import com.syncduo.server.model.restic.backup.Summary;
import com.syncduo.server.model.restic.cat.CatConfig;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.model.restic.init.Init;
import com.syncduo.server.model.restic.stats.Stats;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.db.impl.SystemConfigService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ResticFacadeService {

    private final ResticService resticService;

    private final SystemConfigService systemConfigService;

    private final BackupJobService backupJobService;

    private final ThreadPoolTaskScheduler systemManagementTaskScheduler;

    private final SyncFlowService syncFlowService;

    public ResticFacadeService(
            ResticService resticService,
            SystemConfigService systemConfigService,
            BackupJobService backupJobService,
            ThreadPoolTaskScheduler systemManagementTaskScheduler,
            SyncFlowService syncFlowService) {
        this.resticService = resticService;
        this.systemConfigService = systemConfigService;
        this.backupJobService = backupJobService;
        this.systemManagementTaskScheduler = systemManagementTaskScheduler;
        this.syncFlowService = syncFlowService;
    }

    public void init() throws SyncDuoException {
        SystemConfigEntity systemConfig = this.systemConfigService.getSystemConfig();
        EntityValidationUtil.checkSystemConfigEntityValue(systemConfig);
        String backupStoragePath = systemConfig.getBackupStoragePath();
        // 检查是否已经初始化
        ResticExecResult<CatConfig, Void> catConfigResult = this.resticService.catConfig(backupStoragePath);
        if (!catConfigResult.isSuccess()) {
            ResticExecResult<Init, Void> resticExecResult = this.resticService.init(backupStoragePath);
            if (!resticExecResult.isSuccess()) {
                throw new SyncDuoException("init failed.", resticExecResult.getSyncDuoException());
            }
        }
        // 启动定时任务
        this.systemManagementTaskScheduler.scheduleWithFixedDelay(
                this::backup,
                Instant.now().plus(Duration.ofHours(1)),
                Duration.ofMillis(systemConfig.getBackupIntervalMillis())
        );
    }

    public void backup() {
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return;
        }
        String backupStoragePath = this.systemConfigService.getSystemConfig().getBackupStoragePath();
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            String destFolderPath = syncFlowEntity.getDestFolderPath();
            try {
                // SYNC 状态的 syncflow, 才执行backup
                SyncFlowStatusEnum syncFlowStatusEnum = SyncFlowStatusEnum.valueOf(syncFlowEntity.getSyncStatus());
                if (!syncFlowStatusEnum.equals(SyncFlowStatusEnum.SYNC)) {
                    continue;
                }
                // backup
                ResticExecResult<Summary, Error> backupResult = this.resticService.backup(
                        backupStoragePath,
                        destFolderPath
                );
                // 记录 backup job
                if (backupResult.isSuccess()) {
                    this.backupJobService.addSuccessBackupJob(
                            syncFlowEntity.getSyncFlowId(),
                            backupResult.getData()
                    );
                } else {
                    this.backupJobService.addFailBackupJob(
                            syncFlowEntity.getSyncFlowId(),
                            backupResult.getSyncDuoException().getMessage()
                    );
                }
            } catch (SyncDuoException e) {
                log.error("backup failed. syncFlowEntity is {}", syncFlowEntity, e);
            }
        }
    }

    public Stats getStats(String backupStoragePath) throws SyncDuoException {
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        ResticExecResult<CatConfig, Void> catConfigResult = this.resticService.catConfig(backupStoragePath);
        if (!catConfigResult.isSuccess()) {
            throw new SyncDuoException("getStats failed. repository isn't initialized",
                    catConfigResult.getSyncDuoException());
        }
        ResticExecResult<Stats, Void> statsResult = this.resticService.stats(backupStoragePath);
        if (!statsResult.isSuccess()) {
            throw new SyncDuoException("getStats failed. ", statsResult.getSyncDuoException());
        }
        return statsResult.getData();
    }
}
