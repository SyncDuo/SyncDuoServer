package com.syncduo.server.service.restic;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.entity.RestoreJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.restic.backup.BackupError;
import com.syncduo.server.model.restic.backup.BackupSummary;
import com.syncduo.server.model.restic.cat.CatConfig;
import com.syncduo.server.model.restic.global.ExitErrors;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.model.restic.init.Init;
import com.syncduo.server.model.restic.ls.Node;
import com.syncduo.server.model.restic.restore.RestoreError;
import com.syncduo.server.model.restic.restore.RestoreSummary;
import com.syncduo.server.model.restic.stats.Stats;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.RestoreJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

// Restic 设计为单备份仓库, 单密码
@Slf4j
@Service
public class ResticFacadeService {

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final ResticService resticService;

    private final BackupJobService backupJobService;

    private final ThreadPoolTaskScheduler systemManagementTaskScheduler;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    private final RestoreJobService restoreJobService;

    @Value("${syncduo.server.restic.backupIntervalSec}")
    private long RESTIC_BACKUP_INTERVAL;

    @Value("${syncduo.server.restic.restoreAgeSec}")
    private long RESTIC_RESTORE_AGE_SEC;

    @Value("${syncduo.server.restic.restorePath}")
    private String RESTIC_RESTORE_PATH;

    public ResticFacadeService(
            DebounceService debounceService,
            ResticService resticService,
            BackupJobService backupJobService,
            ThreadPoolTaskScheduler systemManagementTaskScheduler,
            SyncFlowService syncFlowService,
            RcloneFacadeService rcloneFacadeService,
            RestoreJobService restoreJobService) {
        this.moduleDebounceService = debounceService.forModule(ResticFacadeService.class.getSimpleName());
        this.resticService = resticService;
        this.backupJobService = backupJobService;
        this.systemManagementTaskScheduler = systemManagementTaskScheduler;
        this.syncFlowService = syncFlowService;
        this.rcloneFacadeService = rcloneFacadeService;
        this.restoreJobService = restoreJobService;
    }

    public void init() {
        if (ObjectUtils.anyNull(RESTIC_BACKUP_INTERVAL, RESTIC_RESTORE_AGE_SEC) ||
                RESTIC_BACKUP_INTERVAL < 1 || RESTIC_RESTORE_AGE_SEC < 1) {
            throw new ValidationException(
                    "restic init failed. " +
                    "RESTIC_BACKUP_INTERVAL:%s or ".formatted(RESTIC_BACKUP_INTERVAL) +
                    "RESTIC_RESTORE_AGE:%s is null.".formatted(RESTIC_RESTORE_AGE_SEC));
        }
        // 检查 restore path 是否存在
        this.rcloneFacadeService.isSourceFolderExist(RESTIC_RESTORE_PATH);
        // 检查备份目录是否已经初始化
        ResticExecResult<CatConfig, ExitErrors> catConfigResult = this.resticService.catConfig();
        // 没有初始化则初始化
        if (!catConfigResult.isSuccess()) {
            ResticExecResult<Init, ExitErrors> initResult = this.resticService.init();
            if (!initResult.isSuccess()) {
                throw new BusinessException("Restic Init failed.", initResult.getBusinessException());
            }
        }
        // 启动定时任务
        this.systemManagementTaskScheduler.scheduleWithFixedDelay(
                this::periodicalBackup,
                Instant.now().plus(Duration.ofHours(1)),
                Duration.ofSeconds(RESTIC_BACKUP_INTERVAL)
        );
        log.info("restic init success.");
    }

    public void manualBackup(SyncFlowEntity syncFlowEntity) {
        EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        this.backup(syncFlowEntity);
    }

    public void periodicalBackup() {
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return;
        }
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            try {
                this.backup(syncFlowEntity);
            } catch (BusinessException e) {
                log.error("periodicalBackup failed. syncFlowEntity is {}", syncFlowEntity, e);
            }
        }
    }

    public Stats getStats() throws BusinessException {
        ResticExecResult<Stats, ExitErrors> statsResult = this.resticService.stats();
        if (!statsResult.isSuccess()) {
            throw new BusinessException("getStats failed. ", statsResult.getBusinessException());
        }
        return statsResult.getData();
    }

    public List<Node> getSnapshotFileInfo(String snapshotId, String pathString) {
        if (StringUtils.isAnyBlank(snapshotId, pathString)) {
            throw new ValidationException("getSnapshotFileInfo failed. " +
                    "snapshotsId or pathString is null");
        }
        ResticExecResult<List<Node>, ExitErrors> lsResult = this.resticService.ls(snapshotId, pathString);
        if (!lsResult.isSuccess()) {
            throw new BusinessException("getSnapshotFileInfo failed.", lsResult.getBusinessException());
        }
        return lsResult.getData();
    }

    private void backup(SyncFlowEntity syncFlowEntity) throws DbException, BusinessException {
        // SYNC 状态的 syncflow, 才执行backup
        SyncFlowStatusEnum syncFlowStatusEnum = SyncFlowStatusEnum.valueOf(syncFlowEntity.getSyncStatus());
        if (!syncFlowStatusEnum.equals(SyncFlowStatusEnum.SYNC)) {
            return;
        }
        // backup
        ResticExecResult<BackupSummary, List<BackupError>> backupResult = this.resticService.backup(
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
                    backupResult.getBusinessException().getSyncDuoMessage()
            );
        }
    }

    public Path restoreFiles(List<SnapshotFileInfo> snapshotFileInfoList) {
        EntityValidationUtil.isSnapshotFileInfoListValid(snapshotFileInfoList);
        String snapshotId = snapshotFileInfoList.get(0).getSnapshotId();
        String[] pathStrings = snapshotFileInfoList.stream().map(SnapshotFileInfo::getPath).toArray(String[]::new);
        // 创建临时目录
        String restoreTargetPathString = FilesystemUtil.createRandomEnglishFolder(this.RESTIC_RESTORE_PATH);
        // 添加 restore job
        RestoreJobEntity restoreJobEntity = this.restoreJobService.addRunningRestoreJob(
                snapshotId,
                restoreTargetPathString
        );
        // 创建删除 tmp folder 任务
        this.moduleDebounceService.schedule(
                () -> FilesystemUtil.deleteFolder(restoreTargetPathString),
                RESTIC_RESTORE_AGE_SEC
        );
        ResticExecResult<RestoreSummary, List<RestoreError>> restoreResult = this.resticService.restore(
                snapshotId,
                pathStrings,
                restoreTargetPathString
        );
        if (!restoreResult.isSuccess()) {
            BusinessException restoreException = restoreResult.getBusinessException();
            this.restoreJobService.updateRestoreJobAsFailed(
                    restoreJobEntity.getRestoreJobId(),
                    restoreException.toString()
            );
            throw new BusinessException("restoreFiles failed.", restoreResult.getBusinessException());
        }
        // 记录成功日志
        this.restoreJobService.updateRestoreJobAsSuccess(
                restoreJobEntity.getRestoreJobId(),
                restoreResult.getData()
        );
        // snapshot id 前八字符是 shortId
        return FilesystemUtil.zipAllFile(restoreTargetPathString, "restore-" + snapshotId.substring(0, 9));
    }


    public Path restoreFile(SnapshotFileInfo snapshotFileInfo) {
        EntityValidationUtil.isSnapshotFileInfoListValid(Collections.singletonList(snapshotFileInfo));
        String restoreTargetPathString = FilesystemUtil.createRandomEnglishFolder(this.RESTIC_RESTORE_PATH);
        // 添加 restore job
        RestoreJobEntity restoreJobEntity = this.restoreJobService.addRunningRestoreJob(
                snapshotFileInfo.getSnapshotId(),
                restoreTargetPathString
        );
        // 创建删除 tmp folder 任务
        this.moduleDebounceService.schedule(
                () -> FilesystemUtil.deleteFolder(restoreTargetPathString),
                RESTIC_RESTORE_AGE_SEC
        );
        ResticExecResult<RestoreSummary, List<RestoreError>> restoreResult = this.resticService.restore(
                snapshotFileInfo.getSnapshotId(),
                new String[]{snapshotFileInfo.getPath()},
                restoreTargetPathString
        );
        // 记录失败日志
        if (!restoreResult.isSuccess() || restoreResult.getData().getFilesRestored().intValue() < 1) {
            BusinessException restoreException = restoreResult.getBusinessException();
            this.restoreJobService.updateRestoreJobAsFailed(
                    restoreJobEntity.getRestoreJobId(),
                    restoreException.toString()
            );
            throw new BusinessException("restoreFile failed.", restoreException);
        }
        // 记录成功日志
        this.restoreJobService.updateRestoreJobAsSuccess(
                restoreJobEntity.getRestoreJobId(),
                restoreResult.getData()
        );
        return FilesystemUtil.getAllFile(Paths.get(restoreTargetPathString)).get(0);
    }
}
