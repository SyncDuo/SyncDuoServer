package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.DeleteSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowResponse;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.service.bussiness.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

// todo
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final FileService fileService;

    private final SyncFlowService syncFlowService;

    private final FolderService rootFolderService;

    private final FileEventService fileEventService;

    private final SyncSettingService syncSettingService;

    private final FolderWatcher folderWatcher;

    private final FileSyncMappingService fileSyncMappingService;

    private final SystemConfigService systemConfigService;

    private SyncFlowResponse syncFlowResponse;

    private static final String testParentPath = "/home/nopepsi-lenovo-laptop/SyncDuoServer/src/test/resources";

    private static final String sourceFolderName = "sourceFolder";

    private static final String sourceFolderPath = testParentPath + "/" + sourceFolderName;

    private static final String contentFolderParentPath = testParentPath + "/contentParentFolder";

    private static final String contentFolderPath = contentFolderParentPath + "/" + sourceFolderName;

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            FileService fileService,
            SyncFlowService syncFlowService,
            FolderService rootFolderService,
            FileEventService fileEventService,
            SyncSettingService syncSettingService,
            FolderWatcher folderWatcher,
            FileSyncMappingService fileSyncMappingService,
            SystemConfigService systemConfigService) {
        this.syncFlowController = syncFlowController;
        this.fileService = fileService;
        this.syncFlowService = syncFlowService;
        this.fileSyncMappingService = fileSyncMappingService;
        this.systemConfigService = systemConfigService;
        this.rootFolderService = rootFolderService;
        this.fileEventService = fileEventService;
        this.syncSettingService = syncSettingService;
        this.folderWatcher = folderWatcher;
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlowFlatten() {
        createSyncFlowFlatten();
        for (int i = 0; i < 13; i++) {
            waitAllFileHandle();
        }

        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(
                Long.valueOf(this.syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId())
        );
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlow() {
        createSyncFlowMirror();
        waitAllFileHandle();

        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(
                Long.valueOf(
                        this.syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId())
        );
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
    }

    @Test
    void ShouldReturnTrueWhenCreateAndDeleteSyncFlow() {
        createSyncFlowMirror();

        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(
                Long.valueOf(
                        this.syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId()
                )
        );
        SyncFlowResponse syncFlowResponse1 = this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        assert syncFlowResponse1.getCode() == 200;
    }

    void createSyncFlowMirror() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setSyncSetting(SyncSettingEnum.MIRROR.name());
        createSyncFlowRequest.setSyncFlowName("test");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.SYNC.name());
        this.syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert this.syncFlowResponse.getCode() == 200;
    }

    void createSyncFlowFlatten() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setSyncSetting(SyncSettingEnum.FLATTEN_FOLDER.name());
        createSyncFlowRequest.setSyncFlowName("test");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.SYNC.name());
        this.syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
    }

    void createSyncFlowTransform() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderPath);
        createSyncFlowRequest.setSyncSetting(SyncSettingEnum.MIRROR.name());
        createSyncFlowRequest.setSyncFlowName("test");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.TRANSFORM.name());
        this.syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
    }

    void createSyncFlowTransformFlatten() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderPath);
        createSyncFlowRequest.setSyncSetting(SyncSettingEnum.FLATTEN_FOLDER.name());
        createSyncFlowRequest.setSyncFlowName("test");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.TRANSFORM.name());
        this.syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
    }

    void waitAllFileHandle() {
        try {
            Thread.sleep(1000 * 6);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void waitSingleFileHandle(Long rootFolderId) throws SyncDuoException {
        this.folderWatcher.manualCheckFolder(rootFolderId);
        try {
            Thread.sleep(1000 * 2);
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
        systemConfigEntity.setSyncStoragePath(contentFolderParentPath);
        this.systemConfigService.updateConfigEntity(systemConfigEntity);
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
    }

    void truncateAllTable() {
        // root folder truncate
        List<FolderEntity> dbResult = this.rootFolderService.list();
        if (CollectionUtils.isNotEmpty(dbResult)) {
            this.rootFolderService.
                    removeBatchByIds(
                            dbResult.stream().map(FolderEntity::getFolderId).collect(Collectors.toList()));
        }
        // file entity truncate
        List<FileEntity> fileEntities = this.fileService.list();
        if (CollectionUtils.isNotEmpty(fileEntities)) {
            this.fileService.removeBatchByIds(
                    fileEntities.stream().map(FileEntity::getFileId).collect(Collectors.toList())
            );
        }
        // file event truncate
        List<FileEventEntity> fileEventEntities = this.fileEventService.list();
        if (CollectionUtils.isNotEmpty(fileEventEntities)) {
            this.fileEventService.removeBatchByIds(
                    fileEventEntities.stream().map(FileEventEntity::getFileEventId).collect(Collectors.toList())
            );
        }
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
        // file sync mapping truncate
        List<FileSyncMappingEntity> fileSyncMappingEntities = this.fileSyncMappingService.list();
        if (CollectionUtils.isNotEmpty(fileSyncMappingEntities)) {
            this.fileSyncMappingService.removeBatchByIds(
                    fileSyncMappingEntities.stream().map(FileSyncMappingEntity::getFileSyncMappingId).toList()
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
