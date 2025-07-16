package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.syncflow.*;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.model.rclone.operations.check.CheckRequest;
import com.syncduo.server.model.rclone.sync.copy.SyncCopyRequest;
import com.syncduo.server.service.db.impl.*;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.service.rclone.RcloneService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final SyncFlowService syncFlowService;

    private final SyncSettingService syncSettingService;

    private final FolderWatcher folderWatcher;

    private final SystemConfigService systemConfigService;

    private final RcloneFacadeService rcloneFacadeService;

    private SyncFlowEntity syncFlowEntity;

    private static final String testParentPath = "/home/nopepsi-lenovo-laptop/SyncDuoServer/src/test/resources";

    private static final String sourceFolderName = "sourceFolder";

    private static final String sourceFolderPath = testParentPath + "/" + sourceFolderName;

    private static final String contentFolderParentPath = testParentPath + "/contentParentFolder";

    private static final String contentFolderPath = contentFolderParentPath + "/" + sourceFolderName;

    private static final String backupStoragePath = testParentPath + "/backupStoragePath";

    // delay 函数延迟的时间, 单位"秒"
    private static final int DELAY_UNIT = 18;

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            SyncFlowService syncFlowService,
            SyncSettingService syncSettingService,
            FolderWatcher folderWatcher,
            SystemConfigService systemConfigService,
            RcloneFacadeService rcloneFacadeService) {
        this.syncFlowController = syncFlowController;
        this.syncFlowService = syncFlowService;
        this.systemConfigService = systemConfigService;
        this.syncSettingService = syncSettingService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
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
        deleteSyncFlowRequest.setSyncFlowId(syncFlowEntity.getSyncFlowId());
        this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
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
    void ShouldReturnTrueWhenCreateSyncFlowWithFilter() throws IOException {
        String filterCriteria = "[\"*.bin\"]";
        createSyncFlow(filterCriteria);

        List<Path> allFile = FileOperationTestUtil.getAllFile(Path.of(this.syncFlowEntity.getDestFolderPath()));
        for (Path path : allFile) {
            assert !path.getFileName().toString().contains("bin");
        }
    }

    @Test
    void ShouldReturnTrueWhenCreateAndDeleteSyncFlow() {
        createSyncFlow(null);

        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(this.syncFlowEntity.getSyncFlowId());
        SyncFlowResponse syncFlowResponse1 = this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        assert syncFlowResponse1.getCode() == 200;
    }

    void createSyncFlow(String filterCriteria) {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderPath);
        createSyncFlowRequest.setSyncFlowName("test");
        if (StringUtils.isNotBlank(filterCriteria)) {
            createSyncFlowRequest.setFilterCriteria(filterCriteria);
        }
        SyncFlowResponse syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert syncFlowResponse.getCode() == 200;

        waitAllFileHandle();
        this.syncFlowEntity = this.syncFlowService.getById(
                Long.valueOf(
                        syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId())
        );
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
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

    @BeforeEach
    void prepareEnvironment() throws IOException, SyncDuoException {
        this.cleanUp();
        // create folder
        FileOperationTestUtil.createFolders(
                sourceFolderPath,
                4,
                3
        );
        // write system storage path config
        SystemConfigEntity systemConfigEntity = new SystemConfigEntity();
        systemConfigEntity.setBackupStoragePath(backupStoragePath);
        this.systemConfigService.updateSystemConfig(systemConfigEntity);
        log.info("initial finish");
    }

    void cleanUp() {
        // truncate database
        this.truncateAllTable();
        try {
            // delete source folder
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(sourceFolderPath));
        } catch (IOException e) {
            log.warn("删除文件夹失败.", e);
        }
        try {
            // delete dest folder
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(contentFolderParentPath));
        } catch (IOException e) {
            log.error("删除文件夹失败.", e);
        }
        // delete system config cache
        this.systemConfigService.clearCache();
    }

    void truncateAllTable() {
        // sync flow truncate
        List<SyncFlowEntity> syncFlowEntities = this.syncFlowService.list();
        if (CollectionUtils.isNotEmpty(syncFlowEntities)) {
            this.syncFlowService.removeBatchByIds(
                    syncFlowEntities.stream().map(SyncFlowEntity::getSyncFlowId).collect(Collectors.toList())
            );
        }
        // sync setting truncate
        List<SyncSettingEntity> syncSettingEntities = this.syncSettingService.list();
        if (CollectionUtils.isNotEmpty(syncSettingEntities)) {
            this.syncSettingService.removeBatchByIds(
                    syncSettingEntities.stream().map(SyncSettingEntity::getSyncSettingId).collect(Collectors.toList())
            );
        }
        // system config mapping truncate
        List<SystemConfigEntity> systemConfigEntities = this.systemConfigService.list();
        if (CollectionUtils.isNotEmpty(systemConfigEntities)) {
            this.systemConfigService.removeBatchByIds(
                    systemConfigEntities.stream().map(SystemConfigEntity::getSystemConfigId).toList()
            );
        }
        log.info("truncate all table");
    }
}
