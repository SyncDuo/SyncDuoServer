package com.syncduo.server;

import com.syncduo.server.configuration.ApplicationLifeCycleConfig;
import com.syncduo.server.controller.FileSystemAccessController;
import com.syncduo.server.controller.SnapshotsController;
import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.controller.SystemInfoController;
import com.syncduo.server.enums.CommonStatus;
import com.syncduo.server.enums.ResticNodeTypeEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.filesystem.Folder;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.api.snapshots.SnapshotInfo;
import com.syncduo.server.model.api.snapshots.SyncFlowWithSnapshots;
import com.syncduo.server.model.api.syncflow.*;
import com.syncduo.server.model.api.systeminfo.SystemInfo;
import com.syncduo.server.model.api.systeminfo.SystemSettings;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.service.db.impl.*;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.service.restic.ResticFacadeService;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final SyncFlowService syncFlowService;

    private final FolderWatcher folderWatcher;

    private final RcloneFacadeService rcloneFacadeService;

    private final ResticFacadeService resticFacadeService;

    private final CopyJobService copyJobService;

    private final BackupJobService backupJobService;

    private final RestoreJobService restoreJobService;

    private final SnapshotsController snapshotsController;

    private final SystemInfoController systemInfoController;

    private final FileSystemAccessController fileSystemAccessController;

    private final ApplicationLifeCycleConfig applicationLifeCycleConfig;

    private SyncFlowEntity syncFlowEntity;

    @Value("${syncduo.server.system.syncflowDelayDeleteSec}")
    private long delayDeleteSec;

    @Value("${syncduo.server.test.sourceFolder}")
    private String sourceFolderPath;

    @Value("${syncduo.server.test.contentParentFolder}")
    private String contentFolderParentPath;

    @Value("${syncduo.server.restic.backupPath}")
    private String backupPath;

    @Value("${syncduo.server.restic.restorePath}")
    private String restorePath;

    @Value("${syncduo.server.restic.restoreAgeSec}")
    private long restoreAgeSec;

    // delay 函数延迟的时间, 单位"秒"
    private static final int DELAY_UNIT = 18;

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            RcloneFacadeService rcloneFacadeService,
            ResticFacadeService resticFacadeService,
            CopyJobService copyJobService,
            BackupJobService backupJobService,
            RestoreJobService restoreJobService,
            SnapshotsController snapshotsController,
            SystemInfoController systemInfoController,
            FileSystemAccessController fileSystemAccessController,
            ApplicationLifeCycleConfig applicationLifeCycleConfig) {
        this.syncFlowController = syncFlowController;
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
        this.resticFacadeService = resticFacadeService;
        this.copyJobService = copyJobService;
        this.backupJobService = backupJobService;
        this.restoreJobService = restoreJobService;
        this.snapshotsController = snapshotsController;
        this.systemInfoController = systemInfoController;
        this.fileSystemAccessController = fileSystemAccessController;
        this.applicationLifeCycleConfig = applicationLifeCycleConfig;
    }

    @Test
    void ShouldReturnTrueWhenGettingHostName() {
        SyncDuoHttpResponse<String> hostName = this.fileSystemAccessController.getHostName();
        assert StringUtils.isNotBlank(hostName.getData());
    }

    @Test
    void ShouldReturnTrueWhenGettingSubfolders() {
        SyncDuoHttpResponse<List<Folder>> subfolders =
                this.fileSystemAccessController.getSubfolders(sourceFolderPath);
        List<Folder> data = subfolders.getData();
        assert CollectionUtils.isNotEmpty(data);
        for (Folder folder : data) {
            assert ObjectUtils.isNotEmpty(folder);
        }
    }

    @Test
    void ShouldReturnTrueWhenGettingSystemSettings() {
        SyncDuoHttpResponse<SystemSettings> result = this.systemInfoController.getSystemSettings();
        assert result.getStatusCode() == 200;
        SystemSettings systemSettings = result.getData();
        assert ObjectUtils.isNotEmpty(systemSettings);
        assert ObjectUtils.allNotNull(
                systemSettings.getSystem(),
                systemSettings.getRclone(),
                systemSettings.getRestic());
    }

    @Test
    void ShouldReturnTrueWhenGettingSystemInfo() {
        // 创建 syncflow
        createSyncFlow(null);
        SyncDuoHttpResponse<SystemInfo> result = this.systemInfoController.getSystemInfo();
        assert result.getStatusCode() == 200;
        SystemInfo systemInfo = result.getData();
        assert ObjectUtils.isNotEmpty(systemInfo);
        assert ObjectUtils.allNotNull(
                systemInfo.getFolderStats(),
                systemInfo.getWatchers(),
                systemInfo.getSyncFlowNumber()
        );
        assert !StringUtils.isAnyBlank(
                systemInfo.getFileCopyRate(),
                systemInfo.getUptime(),
                systemInfo.getHostName()
        );
    }

    @Test
    void ShouldReturnTrueWhenDownloadFile() throws SyncDuoException, IOException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.resticFacadeService.manualBackup(this.syncFlowEntity);
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        // 获取 snapshot file info
        SyncDuoHttpResponse<List<SnapshotFileInfo>> snapshotFileInfoResponse = this.snapshotsController.getSnapshotFiles(
                syncFlowWithSnapshots.getData().getSnapshotInfoList().get(0).getBackupJobId(),
                "/"
        );
        // 下载文件
        for (SnapshotFileInfo snapshotFileInfo : snapshotFileInfoResponse.getData()) {
            if (snapshotFileInfo.getType().equals(ResticNodeTypeEnum.FILE.getType())) {
                ResponseEntity<Resource> response = this.snapshotsController.downloadSnapshotFile(snapshotFileInfo);
                assert response.getStatusCode() == HttpStatus.OK;
                assert ObjectUtils.isNotEmpty(response.getBody());
                assert response.getBody().contentLength() > 0;
                String headersString = response.getHeaders().toString();
                assert headersString.contains(HttpHeaders.CONTENT_DISPOSITION) &&
                        headersString.contains(snapshotFileInfo.getFileName());
                assert headersString.contains(MediaType.APPLICATION_OCTET_STREAM.toString());
            }
        }
        // 验证 restore 数据库记录是否生成
        List<RestoreJobEntity> restoreJobEntities = this.restoreJobService.list();
        assert CollectionUtils.isNotEmpty(restoreJobEntities);
        for (RestoreJobEntity restoreJobEntity : restoreJobEntities) {
            assert restoreJobEntity.getRestoreJobStatus().equals(CommonStatus.SUCCESS.name());
        }
        // 验证临时文件是否删除
        waitSec(restoreAgeSec * 2);
        assert CollectionUtils.isEmpty(FilesystemUtil.getSubFolders(restorePath));
    }

    @Test
    void ShouldReturnTrueWhenDownloadFiles() throws SyncDuoException, IOException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.resticFacadeService.manualBackup(this.syncFlowEntity);
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        // 获取 snapshot file info
        SyncDuoHttpResponse<List<SnapshotFileInfo>> snapshotFileInfoResponse = this.snapshotsController.getSnapshotFiles(
                syncFlowWithSnapshots.getData().getSnapshotInfoList().get(0).getBackupJobId(),
                "/"
        );
        // 下载多个文件
        ResponseEntity<Resource> response = this.snapshotsController.downloadSnapshotFiles(
                snapshotFileInfoResponse.getData()
        );
        assert response.getStatusCode() == HttpStatus.OK;
        assert ObjectUtils.isNotEmpty(response.getBody());
        assert response.getBody().contentLength() > 0;
        String headersString = response.getHeaders().toString();
        assert headersString.contains(HttpHeaders.CONTENT_DISPOSITION) && headersString.contains("zip");
        assert headersString.contains(MediaType.APPLICATION_OCTET_STREAM.toString());
        // 验证 restore 数据库记录是否生成
        List<RestoreJobEntity> restoreJobEntities = this.restoreJobService.list();
        assert CollectionUtils.isNotEmpty(restoreJobEntities);
        for (RestoreJobEntity restoreJobEntity : restoreJobEntities) {
            assert restoreJobEntity.getRestoreJobStatus().equals(CommonStatus.SUCCESS.name());
        }
        // 验证临时文件是否删除
        waitSec(restoreAgeSec * 2);
        assert CollectionUtils.isEmpty(FilesystemUtil.getSubFolders(restorePath));
    }

    @Test
    void ShouldReturnTrueWhenGetAllSyncFlowWithSnapshots() throws SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 获取 全部 syncflow with snapshots
        SyncDuoHttpResponse<List<SyncFlowWithSnapshots>> allSyncFlowWithSnapshots =
                this.snapshotsController.getAllSyncFlowWithSnapshots();
        assert CollectionUtils.isNotEmpty(allSyncFlowWithSnapshots.getData());
        assert CollectionUtils.isEmpty(allSyncFlowWithSnapshots.getData().get(0).getSnapshotInfoList());
        // 手动触发 backup job
        this.resticFacadeService.manualBackup(this.syncFlowEntity);
        allSyncFlowWithSnapshots = this.snapshotsController.getAllSyncFlowWithSnapshots();
        assert CollectionUtils.isNotEmpty(allSyncFlowWithSnapshots.getData());
    }

    @Test
    void ShouldReturnTureWhenBackupTwice() throws SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.resticFacadeService.manualBackup(this.syncFlowEntity);
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        // 再手动触发 backup
        this.resticFacadeService.manualBackup(syncFlowEntity);
        syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        List<SnapshotInfo> snapshotInfoList = syncFlowWithSnapshots.getData().getSnapshotInfoList();
        assert StringUtils.isBlank(snapshotInfoList.get(0).getSnapshotId());
    }

    @Test
    void ShouldReturnTrueWhenBackup() throws SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.resticFacadeService.manualBackup(this.syncFlowEntity);
        // 获取 copy job
        List<BackupJobEntity> result = this.backupJobService.getBySyncFlowId(this.syncFlowEntity.getSyncFlowId());
        assert CollectionUtils.isNotEmpty(result);
        for (BackupJobEntity backupJobEntity : result) {
            log.debug("backJobEntity is {}", backupJobEntity.toString());
            assert backupJobEntity.getBackupJobStatus().equals(CommonStatus.SUCCESS.name());
        }
        // 手动触发, snapshot 不应该被创建
        this.resticFacadeService.manualBackup(this.syncFlowEntity);
        // 获取 copy job
        BackupJobEntity dbResult = this.backupJobService.getBySyncFlowId(this.syncFlowEntity.getSyncFlowId()).get(1);
        assert StringUtils.isBlank(dbResult.getSnapshotId());
    }

    @Test
    void ShouldReturnTrueWhenResumeSyncFlow() throws SyncDuoException, IOException {
        // 创建 syncflow
        createSyncFlow(null);
        // 停止 syncflow
        this.syncFlowController.changeSyncFlowStatus(
                ChangeSyncFlowStatusRequest.builder()
                        .syncFlowId(this.syncFlowEntity.getSyncFlowId().toString())
                        .syncFlowStatus(SyncFlowStatusEnum.PAUSE.name())
                        .build()
        );
        // 源文件夹创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // source and dest should be desync
        assert !this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity);
        // 恢复 syncflow
        this.syncFlowController.changeSyncFlowStatus(
                ChangeSyncFlowStatusRequest.builder()
                        .syncFlowId(this.syncFlowEntity.getSyncFlowId().toString())
                        .syncFlowStatus(SyncFlowStatusEnum.RESUME.name())
                        .build()
        );
        waitAllFileHandle();
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity);
    }

    @Test
    void ShouldReturnTrueWhenDeleteSyncFlow() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlow("[\"txt\"]");
        waitAllFileHandle();
        // 删除 syncflow
        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(syncFlowEntity.getSyncFlowId().toString());
        this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        waitSec(delayDeleteSec * 2);
        assert ObjectUtils.isEmpty(this.syncFlowService.getBySyncFlowId(syncFlowEntity.getSyncFlowId()));
        // 源文件夹创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // source and dest is desync
        assert !this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByDeleteFile() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        waitAllFileHandle();
        // 源文件夹删除文件
        FileOperationTestUtil.deleteFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // 判断 fileEvent 是否处理正确
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByModifyFile() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 源文件夹修改文件
        FileOperationTestUtil.modifyFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // 判断 fileEvent 是否处理正确
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByCreateFile() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 源文件夹创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // 判断 fileEvent 是否处理正确
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity);
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlowWithFilter() throws SyncDuoException {
        String filterCriteria = "[\"*.bin\"]";
        createSyncFlow(filterCriteria);

        List<Path> allFile = FilesystemUtil.getAllFile(Path.of(this.syncFlowEntity.getDestFolderPath()));
        for (Path path : allFile) {
            assert !path.getFileName().toString().contains("bin");
        }
    }

    @Test
    void ShouldReturnTrueWhenCreateAndDeleteSyncFlow() {
        createSyncFlow(null);

        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(this.syncFlowEntity.getSyncFlowId().toString());
        SyncDuoHttpResponse<Void> syncFlowResponse1 = this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        assert syncFlowResponse1.getStatusCode() == 200;
    }

    void createSyncFlow(String filterCriteria) {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderParentPath + "/random1");
        createSyncFlowRequest.setSyncFlowName("test");
        if (StringUtils.isNotBlank(filterCriteria)) {
            createSyncFlowRequest.setFilterCriteria(filterCriteria);
        }
        SyncDuoHttpResponse<SyncFlowInfo> syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert syncFlowResponse.getStatusCode() == 200;
        // 至多重试一次, 直到syncflow status 是 SYNC
        for (int i = 0; i < 2; i++) {
            waitAllFileHandle();
            this.syncFlowEntity = this.syncFlowService.getById(
                    Long.valueOf(syncFlowResponse.getData().getSyncFlowId())
            );
            if (this.syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name())) {
                break;
            }
        }
        assert this.syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
    }

    void waitAllFileHandle() {
        try {
            Thread.sleep(1000 * DELAY_UNIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void waitSingleFileHandle(String folderPath) throws SyncDuoException {
        this.folderWatcher.manualCheckFolder(folderPath);
        try {
            Thread.sleep(1000 * DELAY_UNIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void waitSec(long sec) throws SyncDuoException {
        try {
            Thread.sleep(sec * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void prepareEnvironment() throws IOException, SyncDuoException {
        // 清空数据库
        this.truncateAllTable();
        // 清空文件夹
        this.deleteFolder();
        // create folder
        FileOperationTestUtil.createFolders(
                sourceFolderPath,
                4,
                3
        );
        // 初始化, 因为 @PostConstruct 先执行而 BeforeEach 后执行, 会导致 Restic 的 backupPath 被清掉
        this.applicationLifeCycleConfig.startUp();
        log.info("initial finish");
    }

    void deleteFolder() {
        try {
            // delete source folder
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(sourceFolderPath));
        } catch (IOException e) {
            log.warn("删除source文件夹失败.", e);
        }
        try {
            // delete dest folder
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(contentFolderParentPath));
        } catch (IOException e) {
            log.error("删除dest文件夹失败.", e);
        }
        try {
            // delete backup folder
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(backupPath));
        } catch (IOException e) {
            log.error("删除backup文件夹失败.", e);
        }
        try {
            // delete restore folder
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(restorePath));
        } catch (IOException e) {
            log.error("删除restore文件夹失败.", e);
        }
    }

    void truncateAllTable() {
        // sync flow truncate
        List<SyncFlowEntity> syncFlowEntities = this.syncFlowService.list();
        if (CollectionUtils.isNotEmpty(syncFlowEntities)) {
            this.syncFlowService.removeBatchByIds(
                    syncFlowEntities.stream().map(SyncFlowEntity::getSyncFlowId).collect(Collectors.toList())
            );
        }
        // copy job truncate
        List<CopyJobEntity> copyJobEntities = this.copyJobService.list();
        if (CollectionUtils.isNotEmpty(copyJobEntities)) {
            this.copyJobService.removeBatchByIds(
                    copyJobEntities.stream().map(CopyJobEntity::getCopyJobId).collect(Collectors.toList())
            );
        }
        // backup job truncate
        List<BackupJobEntity> backupJobEntities = this.backupJobService.list();
        if (CollectionUtils.isNotEmpty(backupJobEntities)) {
            this.backupJobService.removeBatchByIds(
                    backupJobEntities.stream().map(BackupJobEntity::getBackupJobId).collect(Collectors.toList())
            );
        }
        // restore job truncate
        List<RestoreJobEntity> restoreJobEntities = this.restoreJobService.list();
        if (CollectionUtils.isNotEmpty(restoreJobEntities)) {
            this.restoreJobService.removeBatchByIds(
                    restoreJobEntities.stream().map(RestoreJobEntity::getRestoreJobId).collect(Collectors.toList())
            );
        }
        log.info("truncate all table");
    }
}
