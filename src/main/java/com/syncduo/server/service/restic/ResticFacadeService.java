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
import com.syncduo.server.model.restic.ls.Node;
import com.syncduo.server.model.restic.stats.Stats;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.db.impl.SystemConfigService;
import com.syncduo.server.service.secret.RsaService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Restic 设计为单备份仓库, 单密码
@Slf4j
@Service
public class ResticFacadeService {

    private final ResticService resticService;

    private final SystemConfigService systemConfigService;

    private final BackupJobService backupJobService;

    private final ThreadPoolTaskScheduler systemManagementTaskScheduler;

    private final SyncFlowService syncFlowService;

    private final RsaService rsaService;

    private String RESTIC_PASSWORD;

    private String RESTIC_STORAGE_PATH;

    private boolean RESTIC_INITIALIZED = false;

    public ResticFacadeService(
            ResticService resticService,
            SystemConfigService systemConfigService,
            BackupJobService backupJobService,
            ThreadPoolTaskScheduler systemManagementTaskScheduler,
            SyncFlowService syncFlowService,
            RsaService rsaService) {
        this.resticService = resticService;
        this.systemConfigService = systemConfigService;
        this.backupJobService = backupJobService;
        this.systemManagementTaskScheduler = systemManagementTaskScheduler;
        this.syncFlowService = syncFlowService;
        this.rsaService = rsaService;
    }

    public void init() throws SyncDuoException {
        SystemConfigEntity systemConfig = this.systemConfigService.getSystemConfig();
        EntityValidationUtil.checkSystemConfigEntityValue(systemConfig);
        RESTIC_STORAGE_PATH = systemConfig.getBackupStoragePath();
        RESTIC_PASSWORD = this.rsaService.decrypt(systemConfig.getBackupPassword());
        RESTIC_INITIALIZED = true;
        // 检查是否已经初始化
        ResticExecResult<CatConfig, Void> catConfigResult = this.resticService.catConfig(
                this.RESTIC_STORAGE_PATH,
                this.RESTIC_PASSWORD
        );
        // 没有初始化则初始化
        if (!catConfigResult.isSuccess()) {
            ResticExecResult<Init, Void> resticExecResult = this.resticService.init(
                    this.RESTIC_STORAGE_PATH,
                    this.RESTIC_PASSWORD
            );
            if (!resticExecResult.isSuccess()) {
                throw new SyncDuoException("init failed.", resticExecResult.getSyncDuoException());
            }
        }
        // 启动定时任务
        this.systemManagementTaskScheduler.scheduleWithFixedDelay(
                this::periodicalBackup,
                Instant.now().plus(Duration.ofHours(1)),
                Duration.ofMillis(systemConfig.getBackupIntervalMillis())
        );
    }

    public void manualBackup(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        try {
            EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity, "");
            this.backup(syncFlowEntity);
        } catch (SyncDuoException e) {
            throw new SyncDuoException("backup failed.", e);
        }
    }

    public void periodicalBackup() {
        if (!RESTIC_INITIALIZED) {
            log.error("RESTIC is not initialized.");
            return;
        }
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            try {
                this.backup(syncFlowEntity);
            } catch (SyncDuoException e) {
                log.error("backup failed. syncFlowEntity is {}", syncFlowEntity, e);
            }
        }
    }

    public Stats getStats(String backupStoragePath) throws SyncDuoException {
        if (!RESTIC_INITIALIZED) {
            throw new SyncDuoException("RESTIC is not initialized.");
        }
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        ResticExecResult<CatConfig, Void> catConfigResult = this.resticService.catConfig(
                this.RESTIC_STORAGE_PATH,
                this.RESTIC_PASSWORD
        );
        if (!catConfigResult.isSuccess()) {
            throw new SyncDuoException("getStats failed. repository isn't initialized",
                    catConfigResult.getSyncDuoException());
        }
        ResticExecResult<Stats, Void> statsResult = this.resticService.stats(
                this.RESTIC_STORAGE_PATH,
                this.RESTIC_PASSWORD
        );
        if (!statsResult.isSuccess()) {
            throw new SyncDuoException("getStats failed. ", statsResult.getSyncDuoException());
        }
        return statsResult.getData();
    }

    public List<Node> getSnapshotFileInfo(String snapshotId, String pathString) throws SyncDuoException {
        if (!RESTIC_INITIALIZED) {
            throw new SyncDuoException("getSnapshotFileInfo failed. RESTIC is not initialized.");
        }
        if (StringUtils.isAnyBlank(snapshotId, pathString)) {
            throw new SyncDuoException("getSnapshotFileInfo failed. " +
                    "snapshotsId or pathString is null");
        }
        ResticExecResult<Node, Void> lsResult = this.resticService.ls(
                RESTIC_STORAGE_PATH,
                RESTIC_PASSWORD,
                snapshotId,
                pathString
        );
        if (!lsResult.isSuccess()) {
            throw new SyncDuoException("getSnapshotFileInfo failed.", lsResult.getSyncDuoException());
        }
        return lsResult.getAggData();
    }

    private void backup(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // SYNC 状态的 syncflow, 才执行backup
        SyncFlowStatusEnum syncFlowStatusEnum = SyncFlowStatusEnum.valueOf(syncFlowEntity.getSyncStatus());
        if (!syncFlowStatusEnum.equals(SyncFlowStatusEnum.SYNC)) {
            return;
        }
        // backup
        ResticExecResult<Summary, Error> backupResult = this.resticService.backup(
                this.RESTIC_STORAGE_PATH,
                this.RESTIC_PASSWORD,
                syncFlowEntity.getDestFolderPath()
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
    }
}
