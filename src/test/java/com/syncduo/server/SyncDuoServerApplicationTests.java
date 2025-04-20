package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.DeleteSyncFlowRequest;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowInfo;
import com.syncduo.server.model.dto.http.syncflow.SyncFlowResponse;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.service.bussiness.impl.*;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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

    // delay 函数延迟的时间, 单位"秒"
    private static final int DELAY_UNIT = 6;

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
    void ShouldReturnTrueWhenDeleteSyncFlow() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowTransform("[\"txt\"]");
        waitAllFileHandle();
        // 删除 syncflow
        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(Long.valueOf(
                this.syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId())
        );
        this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        // 源文件夹创建文件
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // 目的文件夹没有文件增加
        List<Path> allFileInFolder = FilesystemUtil.getAllFileInFolder(contentFolderPath);
        boolean sameTextFile = false;
        boolean sameBinFile = false;
        for (Path file : allFileInFolder) {
            if (txtAndBinFile.getLeft().getFileName().equals(file.getFileName())) {
                sameTextFile = true;
            } else if (txtAndBinFile.getRight().getFileName().equals(file.getFileName())) {
                sameBinFile = true;
            }
        }

        assert !sameTextFile && !sameBinFile;
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByDeleteFileTransform() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowTransform("[\"txt\"]");
        waitAllFileHandle();
        // 源文件夹修改文件
        List<Path> modifiedFile = FileOperationTestUtil.deleteFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitAllFileHandle();
        // 判断 fileEvent 是否处理正确
        modifiedFile.removeIf(file -> file.getFileName().toString().contains("txt"));
        checkIsFileEventHandleCorrectWhenDeleteFile(modifiedFile);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByModifyFileTransform() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowTransform("[\"txt\"]");
        waitAllFileHandle();
        // 源文件夹修改文件
        List<Path> modifiedFile = FileOperationTestUtil.modifyFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitAllFileHandle();
        // 判断 fileEvent 是否处理正确
        modifiedFile.removeIf(file -> file.getFileName().toString().contains("txt"));
        checkIsFileEventHandleCorrectWhenChangeFile(modifiedFile);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByCreateFileTransform() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowTransform("[\"txt\"]");
        waitAllFileHandle();
        // 源文件夹创建文件
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        List<Path> files = new ArrayList<>();
        files.add(txtAndBinFile.getRight());
        // 判断 fileEvent 是否处理正确
        checkIsFileEventHandleCorrectWhenCreateFile(files);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByDeleteFileMirrored() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowMirror();
        waitAllFileHandle();
        // 源文件夹修改文件
        List<Path> modifiedFile = FileOperationTestUtil.deleteFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitAllFileHandle();
        // 判断 fileEvent 是否处理正确
        checkIsFileEventHandleCorrectWhenDeleteFile(modifiedFile);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByModifyFileMirrored() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowMirror();
        waitAllFileHandle();
        // 源文件夹修改文件
        List<Path> modifiedFile = FileOperationTestUtil.modifyFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitAllFileHandle();
        // 判断 fileEvent 是否处理正确
        checkIsFileEventHandleCorrectWhenChangeFile(modifiedFile);
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByCreateFileMirrored() throws IOException, SyncDuoException {
        // 创建 syncflow
        createSyncFlowMirror();
        waitAllFileHandle();
        // 源文件夹创建文件
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        List<Path> files = new ArrayList<>();
        files.add(txtAndBinFile.getLeft());
        files.add(txtAndBinFile.getRight());
        // 判断 fileEvent 是否处理正确
        checkIsFileEventHandleCorrectWhenCreateFile(files);
    }

    private void checkIsFileEventHandleCorrectWhenDeleteFile(List<Path> files) throws SyncDuoException {
        // 等待文件处理
        waitAllFileHandle();
        // 判断 syncflow 状态是否为 sync
        SyncFlowInfo syncFlowInfo = this.syncFlowResponse.getSyncFlowInfoList().get(0);
        SyncFlowEntity syncFlowEntity =
                this.syncFlowService.getBySyncFlowIdFromCache(Long.valueOf(syncFlowInfo.getSyncFlowId()));
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
        for (Path file : files) {
            // 判断源文件夹是否正确响应
            FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                    syncFlowEntity.getSourceFolderId(),
                    sourceFolderPath,
                    file
            );
            assert ObjectUtils.isEmpty(fileEntity);
        }
        // 获取 desync 的 fileSyncMapping, 判断与 files 的数量是否一致
        List<FileSyncMappingEntity> fileSyncMappingEntityList =
                this.fileSyncMappingService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        int count = 0;
        for (FileSyncMappingEntity fileSyncMappingEntity : fileSyncMappingEntityList) {
            if (fileSyncMappingEntity.getFileDesync().equals(FileDesyncEnum.FILE_DESYNC.getCode())) {
                count++;
            }
        }
        assert count == files.size();
    }

    private void checkIsFileEventHandleCorrectWhenChangeFile(List<Path> files) throws SyncDuoException {
        // 等待文件处理
        waitAllFileHandle();
        // 判断 syncflow 状态是否为 sync
        SyncFlowInfo syncFlowInfo = this.syncFlowResponse.getSyncFlowInfoList().get(0);
        SyncFlowEntity syncFlowEntity =
                this.syncFlowService.getBySyncFlowIdFromCache(Long.valueOf(syncFlowInfo.getSyncFlowId()));
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
        for (Path file : files) {
            // 判断源文件夹是否正确响应
            FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                    syncFlowEntity.getSourceFolderId(),
                    sourceFolderPath,
                    file
            );
            assert fileEntity.getFileMd5Checksum().equals(FilesystemUtil.getMD5Checksum(file));
            // 判断目的文件夹是否正确响应
            FileSyncMappingEntity fileSyncMapping = this.fileSyncMappingService.getBySyncFlowIdAndSourceFileId(
                    syncFlowEntity.getSyncFlowId(),
                    fileEntity.getFileId()
            );
            FileEntity destFileEntity = this.fileService.getById(fileSyncMapping.getDestFileId());
            assert destFileEntity.getFileMd5Checksum().equals(FilesystemUtil.getMD5Checksum(file));
        }
    }

    private void checkIsFileEventHandleCorrectWhenCreateFile(List<Path> files) throws SyncDuoException {
        // 等待文件处理
        waitAllFileHandle();
        // 判断 syncflow 状态是否为 sync
        SyncFlowInfo syncFlowInfo = this.syncFlowResponse.getSyncFlowInfoList().get(0);
        SyncFlowEntity syncFlowEntity =
                this.syncFlowService.getBySyncFlowIdFromCache(Long.valueOf(syncFlowInfo.getSyncFlowId()));
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
        for (Path file : files) {
            // 判断源文件夹是否正确响应
            FileEntity fileEntity = this.fileService.getFileEntityFromFile(
                    syncFlowEntity.getSourceFolderId(),
                    sourceFolderPath,
                    file
            );
            assert ObjectUtils.isNotEmpty(fileEntity);
            // 判断目的文件夹是否正确响应
            FileSyncMappingEntity txtFileSyncMapping = this.fileSyncMappingService.getBySyncFlowIdAndSourceFileId(
                    syncFlowEntity.getSyncFlowId(),
                    fileEntity.getFileId()
            );
            assert ObjectUtils.isNotEmpty(txtFileSyncMapping);
        }
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlowTransformFlatten() {
        // fixme: 处理 fileSystemEvent 的时候会报错 fileEntity 已存在
        String filterCriteria = "[\"bin\"]";
        createSyncFlowTransformFlatten(filterCriteria);
        waitAllFileHandle();

        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(
                Long.valueOf(
                        this.syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId())
        );
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlowTransform() {
        // fixme: 处理 fileSystemEvent 的时候会报错 fileEntity 已存在
        String filterCriteria = "[\"bin\"]";
        createSyncFlowTransform(filterCriteria);
        waitAllFileHandle();

        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(
                Long.valueOf(
                        this.syncFlowResponse.getSyncFlowInfoList().get(0).getSyncFlowId())
        );
        assert syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlowSync() {
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

    void createSyncFlowTransform(String filterCriteria) {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderPath);
        createSyncFlowRequest.setSyncSetting(SyncSettingEnum.MIRROR.name());
        createSyncFlowRequest.setFilterCriteria(filterCriteria);
        createSyncFlowRequest.setSyncFlowName("test");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.TRANSFORM.name());
        this.syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
    }

    void createSyncFlowTransformFlatten(String filterCriteria) {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderPath);
        createSyncFlowRequest.setSyncSetting(SyncSettingEnum.FLATTEN_FOLDER.name());
        createSyncFlowRequest.setFilterCriteria(filterCriteria);
        createSyncFlowRequest.setSyncFlowName("test");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.TRANSFORM.name());
        this.syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
    }

    void waitAllFileHandle() {
        try {
            Thread.sleep(1000 * DELAY_UNIT);
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
