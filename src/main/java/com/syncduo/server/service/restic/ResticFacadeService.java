package com.syncduo.server.service.restic;

import com.syncduo.server.enums.CommonStatus;
import com.syncduo.server.enums.ResticNodeTypeEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.exception.ResourceNotFoundException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.entity.RestoreJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.restic.cat.CatConfig;
import com.syncduo.server.model.restic.global.ExitErrors;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.model.restic.init.Init;
import com.syncduo.server.model.restic.ls.Node;
import com.syncduo.server.model.restic.stats.Stats;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.RestoreJobService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Restic 设计为单备份仓库, 单密码
@Slf4j
@Service
public class ResticFacadeService {

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final ResticService resticService;

    private final BackupJobService backupJobService;

    private final RcloneFacadeService rcloneFacadeService;

    private final RestoreJobService restoreJobService;

    @Value("${syncduo.server.system.backupIntervalMillis}")
    private long SYSTEM_BACKUP_INTERVAL;

    @Value("${syncduo.server.restic.restoreAgeSec}")
    private long RESTIC_RESTORE_AGE_SEC;

    @Value("${syncduo.server.restic.restorePath}")
    private String RESTIC_RESTORE_PATH;

    public ResticFacadeService(
            DebounceService debounceService,
            ResticService resticService,
            BackupJobService backupJobService,
            RcloneFacadeService rcloneFacadeService,
            RestoreJobService restoreJobService) {
        this.moduleDebounceService = debounceService.forModule(ResticFacadeService.class.getSimpleName());
        this.resticService = resticService;
        this.backupJobService = backupJobService;
        this.rcloneFacadeService = rcloneFacadeService;
        this.restoreJobService = restoreJobService;
    }

    public void init() {
        try {
            if (ObjectUtils.anyNull(SYSTEM_BACKUP_INTERVAL, RESTIC_RESTORE_AGE_SEC) ||
                    SYSTEM_BACKUP_INTERVAL < 1 || RESTIC_RESTORE_AGE_SEC < 1) {
                throw new ValidationException(
                        "RESTIC_BACKUP_INTERVAL:%s or ".formatted(SYSTEM_BACKUP_INTERVAL) +
                                "RESTIC_RESTORE_AGE:%s is null.".formatted(RESTIC_RESTORE_AGE_SEC));
            }
            // 检查 restore path 是否存在
            this.rcloneFacadeService.isSourceFolderExist(RESTIC_RESTORE_PATH);
            // 删除 restore path 的所有子文件夹
            FilesystemUtil.deleteFolder(RESTIC_RESTORE_PATH, false);
            // 逻辑删除所有restore记录
            this.restoreJobService.deleteAllRecord();
            // 检查备份目录是否已经初始化
            ResticExecResult<CatConfig, ExitErrors> catConfigResult = this.resticService.catConfig();
            // 没有初始化则初始化
            if (!catConfigResult.isSuccess()) {
                ResticExecResult<Init, ExitErrors> initResult = this.resticService.init();
                if (!initResult.isSuccess()) {
                    throw new BusinessException("execute restic init method failed",
                            initResult.msg());
                }
            }
        } catch (Exception e) {
            throw new BusinessException("Restic init failed", e);
        }
        log.info("restic init success.");
    }

    public Stats getStats() throws BusinessException {
        ResticExecResult<Stats, ExitErrors> statsResult = this.resticService.stats();
        if (!statsResult.isSuccess()) {
            throw new BusinessException("getStats failed. ", statsResult.msg());
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
            throw new BusinessException("getSnapshotFileInfo failed.", lsResult.msg());
        }
        return lsResult.getData();
    }

    @Async("generalTaskScheduler")
    public void backup(SyncFlowEntity syncFlowEntity) throws DbException, BusinessException {
        // 判断是否允许 backup
        if (SyncFlowStatusEnum.isBackupProhibit(syncFlowEntity.getSyncStatus())) {
            return;
        }
        BackupJobEntity backupJobEntity = this.backupJobService.addBackupJob(syncFlowEntity.getSyncFlowId());
        // backup
        this.resticService.backup(syncFlowEntity.getDestFolderPath())
                .thenCompose(backupResult -> {
                    if (backupResult.isSuccess()) {
                        this.backupJobService.updateSuccessBackupJob(backupJobEntity, backupResult.getData());
                    } else {
                        BusinessException ex = backupResult.msg();
                        this.backupJobService.updateFailBackupJob(backupJobEntity, ex.toString());
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    public long submitRestoreJob(List<SnapshotFileInfo> snapshotFileInfoList) {
        SnapshotFileInfo firstSnapshotFileInfo = snapshotFileInfoList.get(0);
        String snapshotId = firstSnapshotFileInfo.getSnapshotId();
        // 先查询数据库是否已经 restore
        RestoreJobEntity dbResult = this.searchRestoreFile(firstSnapshotFileInfo);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            return dbResult.getRestoreJobId();
        }
        // 创建临时目录
        String restoreRootPath = FilesystemUtil.createRandomEnglishFolder(this.RESTIC_RESTORE_PATH);
        // 判断是否将 origin file path 放入数据库
        if (snapshotFileInfoList.size() > 1) {
            RestoreJobEntity restoreJobEntity = this.restoreJobService.addRestoreJob(snapshotId, restoreRootPath);
            // fire and forget
            this.restoreFiles(snapshotFileInfoList, restoreJobEntity);
            return restoreJobEntity.getRestoreJobId();
        }
        RestoreJobEntity restoreJobEntity = this.restoreJobService.addRestoreJob(
                snapshotId,
                firstSnapshotFileInfo.getPath(),
                restoreRootPath);
        // fire and forget
        if (this.isRestoreAsZipFile(firstSnapshotFileInfo)) {
            this.restoreFiles(snapshotFileInfoList, restoreJobEntity);
        } else {
            this.restoreFile(firstSnapshotFileInfo, restoreJobEntity);
        }
        // return restoreJobId
        return restoreJobEntity.getRestoreJobId();
    }

    public RestoreJobEntity searchRestoreFile(SnapshotFileInfo snapshotFileInfo) {
        RestoreJobEntity dbResult = this.restoreJobService.getBySnapshotIdAndOrigFilePath(
                snapshotFileInfo.getSnapshotId(),
                snapshotFileInfo.getPath()
        );
        if (ObjectUtils.isEmpty(dbResult)) {
            return null;
        }
        CommonStatus restoreJobStatus = CommonStatus.fromName(dbResult.getRestoreJobStatus());
        if (restoreJobStatus != CommonStatus.SUCCESS) {
            return null;
        }
        try {
            FilesystemUtil.isFilePathValid(dbResult.getRestoreFullPath());
            // 延长 debounce 删除
            this.delayDeleteRestoreFilesJob(dbResult);
            return dbResult;
        } catch (ValidationException | ResourceNotFoundException e) {
            log.warn("searchRestoreFile failed. RestoreJobEntity:{} valid but restore file not exist.", dbResult);
        }
        return null;
    }

    public Path getRestoreFile(long restoreJobId) {
        RestoreJobEntity dbResult = this.restoreJobService.getByRestoreJobId(restoreJobId);
        if (ObjectUtils.isEmpty(dbResult)) {
            throw new BusinessException("getRestoreFile failed. " +
                    "restore file(restoreJobId:%s) is deleted.".formatted(restoreJobId));
        }
        switch (CommonStatus.fromName(dbResult.getRestoreJobStatus())) {
            case SUCCESS -> {
                return FilesystemUtil.isFilePathValid(dbResult.getRestoreFullPath());
            }
            case RUNNING -> {
                return null;
            }
            case FAILED -> throw new BusinessException("getRestoreFile failed. restore failed." +
                    "restoreJobId is %s, errorMessage is %s".formatted(restoreJobId, dbResult.getErrorMessage()));
            default -> throw new BusinessException("getRestoreFile failed. " +
                    "RestoreJobEntity:%s has UNKNOWN STATUS .".formatted(dbResult));
        }
    }

    @Async("generalTaskScheduler")
    protected void restoreFiles(List<SnapshotFileInfo> snapshotFileInfoList, RestoreJobEntity restoreJobEntity) {
        String snapshotId = snapshotFileInfoList.get(0).getSnapshotId();
        String[] pathStrings = snapshotFileInfoList.stream().map(SnapshotFileInfo::getPath).toArray(String[]::new);
        String restoreRootPath = restoreJobEntity.getRestoreRootPath();
        this.resticService.restore(snapshotId, pathStrings, restoreRootPath)
                .thenCompose(resticRestoreResult -> {
                    if (resticRestoreResult.isSuccess()) {
                        Path restoreFile = FilesystemUtil.zipAllFile(
                                restoreRootPath,
                                "restore-" + snapshotId.substring(0, 9)
                        );
                        // 记录成功日志
                        this.restoreJobService.updateRestoreJobAsSuccess(
                                restoreJobEntity.getRestoreJobId(),
                                resticRestoreResult.getData(),
                                restoreFile.toAbsolutePath().toString()
                        );
                    } else {
                        // 记录失败日志
                        BusinessException restoreException = resticRestoreResult.msg();
                        this.restoreJobService.updateRestoreJobAsFailed(
                                restoreJobEntity.getRestoreJobId(),
                                restoreException.toString()
                        );
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .whenComplete((ignored, ex) -> {
                    try {
                        if (ObjectUtils.isNotEmpty(ex)) {
                            BusinessException businessException = new BusinessException(
                                    "restoreFile failed. restore files:%s".formatted(snapshotFileInfoList), ex);
                            this.restoreJobService.updateRestoreJobAsFailed(
                                    restoreJobEntity.getRestoreJobId(),
                                    businessException.toString()
                            );
                        }
                    } catch (Exception e) {
                        log.error("restoreFile failed. restore files:{}", snapshotFileInfoList, ex);
                    } finally {
                        this.delayDeleteRestoreFilesJob(restoreJobEntity);
                    }
                });
    }

    @Async("generalTaskScheduler")
    protected void restoreFile(SnapshotFileInfo snapshotFileInfo, RestoreJobEntity restoreJobEntity) {
        String snapshotId = snapshotFileInfo.getSnapshotId();
        String[] pathStrings = new String[]{snapshotFileInfo.getPath()};
        String restoreRootPath = restoreJobEntity.getRestoreRootPath();
        this.resticService.restore(snapshotId, pathStrings, restoreRootPath)
                .thenCompose(resticRestoreResult -> {
                    if (resticRestoreResult.isSuccess()) {
                        Path restoreFile = FilesystemUtil.getAllFile(Paths.get(restoreRootPath)).get(0);
                        // 记录成功日志
                        this.restoreJobService.updateRestoreJobAsSuccess(
                                restoreJobEntity.getRestoreJobId(),
                                resticRestoreResult.getData(),
                                restoreFile.toAbsolutePath().toString()
                        );
                    } else {
                        // 记录失败日志
                        BusinessException restoreException = resticRestoreResult.msg();
                        this.restoreJobService.updateRestoreJobAsFailed(
                                restoreJobEntity.getRestoreJobId(),
                                restoreException.toString()
                        );
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .whenComplete((ignored, ex) -> {
                    try {
                        if (ObjectUtils.isNotEmpty(ex)) {
                            BusinessException businessException = new BusinessException(
                                    "restoreFile failed. restore file:%s".formatted(snapshotFileInfo), ex);
                            this.restoreJobService.updateRestoreJobAsFailed(
                                    restoreJobEntity.getRestoreJobId(),
                                    businessException.toString()
                            );
                        }
                    } catch (Exception e) {
                        log.error("restoreFile failed. restore file:{}", snapshotFileInfo, ex);
                    } finally {
                        this.delayDeleteRestoreFilesJob(restoreJobEntity);
                    }
                });
    }

    private void delayDeleteRestoreFilesJob(RestoreJobEntity restoreJobEntity) {
        this.moduleDebounceService.debounce(
                "RestoreJobId::%s::delete".formatted(restoreJobEntity.getRestoreJobId()),
                () -> FilesystemUtil.deleteFolder(restoreJobEntity.getRestoreRootPath(), true),
                RESTIC_RESTORE_AGE_SEC
        );
    }

    private boolean isRestoreAsZipFile(SnapshotFileInfo snapshotFileInfo) {
        ResticNodeTypeEnum resticNodeType = ResticNodeTypeEnum.fromString(snapshotFileInfo.getType());
        if (resticNodeType == ResticNodeTypeEnum.UNKNOWN) {
            throw new BusinessException("isRestoreAsZipFile failed. " +
                    "snapshotFileInfo:%s has UNKNOWN type".formatted(snapshotFileInfo));
        }
        return resticNodeType == ResticNodeTypeEnum.DIRECTORY;
    }
}
