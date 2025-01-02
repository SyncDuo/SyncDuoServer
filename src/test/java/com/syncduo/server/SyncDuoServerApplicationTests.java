package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.CreateSyncFlowRequest;
import com.syncduo.server.model.dto.http.DeleteSyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.mq.producer.RootFolderEventProducer;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final FileService fileService;

    private final SyncFlowService syncFlowService;

    private final AdvancedFileOpService advancedFileOpService;

    private final RootFolderService rootFolderService;

    private final FileEventService fileEventService;

    private final SyncSettingService syncSettingService;

    private final RootFolderEventProducer rootFolderEventProducer;

    private static final String testParentPath = "/home/nopepsi-dev/IdeaProject/SyncDuoServer/src/test/resources";

    private static final String sourceFolderName = "sourceFolder";

    private static final String sourceFolderPath = testParentPath + "/" + sourceFolderName;

    private static final String internalFolderPath = testParentPath + "/." + sourceFolderName;

    private static final String contentFolderParentPath = testParentPath + "/contentParentFolder";

    private static final String contentFolderPath = contentFolderParentPath + "/" + sourceFolderName;

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            FileService fileService,
            SyncFlowService syncFlowService,
            AdvancedFileOpService advancedFileOpService,
            RootFolderService rootFolderService,
            FileEventService fileEventService,
            SyncSettingService syncSettingService,
            RootFolderEventProducer rootFolderEventProducer) {
        this.syncFlowController = syncFlowController;
        this.fileService = fileService;
        this.syncFlowService = syncFlowService;
        this.advancedFileOpService = advancedFileOpService;
        this.rootFolderService = rootFolderService;
        this.fileEventService = fileEventService;
        this.syncSettingService = syncSettingService;
        this.rootFolderEventProducer = rootFolderEventProducer;
    }

    @Test
    void shouldReturnTrueWhenContentFolderFullScanAfterChangeFile() throws IOException, SyncDuoException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert  syncFlowResponse.getCode().equals(200);
        this.waitAllFileHandle();
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        List<Path> allFile = FileOperationTestUtil.getAllFile(Paths.get(contentFolderPath));
        for (Path file : allFile) {
            if (file.getFileName().toString().contains("txt")) {
                // write to text file
                FileOperationTestUtil.writeToTextFile(file);
                boolean isSynced = this.advancedFileOpService.fullScan(contentFolderEntity);
                assert !isSynced;
                this.waitSingleFileHandle(contentFolderEntity.getRootFolderId());
                SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.getById(syncFlowResponse.getData().get(1));
                isSynced = this.advancedFileOpService.isInternal2ContentSyncFlowSync(internal2ContentSyncFlow);
                assert isSynced;
                this.waitSingleFileHandle(contentFolderEntity.getRootFolderId());
                isSynced = this.advancedFileOpService.isInternal2ContentSyncFlowSync(internal2ContentSyncFlow);
                assert isSynced;
            } else {
                FileOperationTestUtil.writeRandomBinaryData(file);
                boolean isSynced = this.advancedFileOpService.fullScan(contentFolderEntity);
                assert !isSynced;
                this.waitSingleFileHandle(contentFolderEntity.getRootFolderId());
                SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.getById(syncFlowResponse.getData().get(1));
                isSynced = this.advancedFileOpService.isInternal2ContentSyncFlowSync(internal2ContentSyncFlow);
                assert isSynced;
                this.waitSingleFileHandle(contentFolderEntity.getRootFolderId());
                isSynced = this.advancedFileOpService.isInternal2ContentSyncFlowSync(internal2ContentSyncFlow);
                assert isSynced;
            }
        }
    }

    @Test
    void shouldReturnTrueWhenContentFolderFullScanAfterCreateFile() throws IOException, SyncDuoException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert  syncFlowResponse.getCode().equals(200);
        this.waitAllFileHandle();
        // manual add file to content folder
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(contentFolderPath));
        Path txtFile = txtAndBinFile.getLeft();
        Path binFile = txtAndBinFile.getRight();
        // full scan
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        boolean isSynced = this.advancedFileOpService.fullScan(contentFolderEntity);
        assert !isSynced;
        this.waitSingleFileHandle(contentFolderEntity.getRootFolderId());
        FileEntity txtFileEntity = this.fileService.getFileEntityFromFile(
                contentFolderEntity.getRootFolderId(),
                contentFolderEntity.getRootFolderFullPath(),
                txtFile
        );
        FileEntity binFileEntity = this.fileService.getFileEntityFromFile(
                contentFolderEntity.getRootFolderId(),
                contentFolderEntity.getRootFolderFullPath(),
                binFile
        );
        assert ObjectUtils.allNotNull(txtFileEntity, binFileEntity);
        // compare
        SyncFlowEntity internal2ContentSyncFlow = this.syncFlowService.getById(syncFlowResponse.getData().get(1));
        isSynced = this.advancedFileOpService.isInternal2ContentSyncFlowSync(internal2ContentSyncFlow);
        assert isSynced;
    }

    // content folder delete file event only work sometime, which is weird
    // because folder delete file event always work on source folder
    @RepeatedTest(value = 10, failureThreshold = 6)
    void shouldReturnTrueWhenContentFolderDeleteFile() throws IOException, SyncDuoException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        // Add a sleep to allow time for handler handling
        this.waitAllFileHandle();
        // get content folder entity
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        // 遍历 file
        List<Path> allFile = FileOperationTestUtil.getAllFile(Path.of(contentFolderPath));
        for (Path file : allFile) {
            // get contentFileEntity
            FileEntity contentFileEntity = this.fileService.getFileEntityFromFile(
                    contentFolderEntity.getRootFolderId(),
                    contentFolderEntity.getRootFolderFullPath(),
                    file
            );
            // manual delete file in content folder
            FileOperationUtils.deleteFile(file);
            // trigger file handle
            this.waitSingleFileHandle(contentFolderEntity.getRootFolderId());
            // check is content file delete
            FileEntity contentFileEntity2 = this.fileService.getFileEntityFromFile(
                    contentFolderEntity.getRootFolderId(),
                    contentFolderEntity.getRootFolderFullPath(),
                    file
            );
            assert ObjectUtils.isEmpty(contentFileEntity2);
        }
    }

    @RepeatedTest(value = 10, failureThreshold = 6)
    void shouldReturnTrueWhenSourceFolderDeleteFile() throws IOException, SyncDuoException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        // Add a sleep to allow time for handler handling
        this.waitAllFileHandle();
        // get source and internal folder entity
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderFullPath(sourceFolderPath);
        RootFolderEntity internalFolderEntity = this.rootFolderService.getByFolderFullPath(internalFolderPath);
        // get content folder entity
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        // manual delete file in source folder
        List<Path> allFile = FileOperationTestUtil.getAllFile(Path.of(sourceFolderPath));
        for (Path file : allFile) {
            // 先获取 sourceFileEntity 和 internalFileEntity 和 contentFileEntity
            FileEntity sourceFileEntity = this.fileService.getFileEntityFromFile(
                    sourceFolderEntity.getRootFolderId(),
                    sourceFolderEntity.getRootFolderFullPath(),
                    file
            );
            FileEntity internalFileEntity = this.fileService.getInternalFileEntityFromSourceEntity(
                    internalFolderEntity.getRootFolderId(),
                    sourceFileEntity
            );
            FileEntity contentFileEntity = this.fileService.getContentFileEntityFromInternalEntity(
                    contentFolderEntity.getRootFolderId(),
                    internalFileEntity,
                    SyncSettingEnum.MIRROR
            );
            assert ObjectUtils.allNotNull(sourceFileEntity, internalFileEntity, contentFileEntity);
            // 删除文件
            FileOperationUtils.deleteFile(file);
            this.waitSingleFileHandle(sourceFolderEntity.getRootFolderId());
            // 判断文件是否已删除
            FileEntity sourceFileEntity2 = this.fileService.getFileEntityFromFile(
                    sourceFolderEntity.getRootFolderId(),
                    sourceFolderEntity.getRootFolderFullPath(),
                    file
            );
            FileEntity internalFileEntity2 = this.fileService.getInternalFileEntityFromSourceEntity(
                    internalFolderEntity.getRootFolderId(),
                    sourceFileEntity
            );
            FileEntity contentFileEntity2 = this.fileService.getContentFileEntityFromInternalEntity(
                    contentFolderEntity.getRootFolderId(),
                    internalFileEntity,
                    SyncSettingEnum.MIRROR
            );
            assert ObjectUtils.isEmpty(sourceFileEntity2);
            assert ObjectUtils.allNotNull(internalFileEntity2, contentFileEntity2);
            // 获取 source folder delete event
            List<FileEventEntity> fileEventEntityList = this.fileEventService.getByFileEventTypeAndFileId(
                    FileEventTypeEnum.FILE_DELETED,
                    sourceFileEntity.getFileId());
            assert CollectionUtils.isNotEmpty(fileEventEntityList);
        }
    }

    @Test
    void shouldReturnTrueWhenSourceFolderChangeFile() throws IOException, SyncDuoException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        // Add a sleep to allow time for handler handling
        this.waitAllFileHandle();
        // get source folder entity
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderFullPath(sourceFolderPath);
        // get content folder entity
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        // manual change file in source folder
        List<Path> allFile = FileOperationTestUtil.getAllFile(Path.of(sourceFolderPath));
        for (Path file : allFile) {
            if (file.getFileName().toString().contains("txt")) {
                FileOperationTestUtil.writeToTextFile(file);
                this.waitSingleFileHandle(sourceFolderEntity.getRootFolderId());
                FileEntity sourceFileEntity = this.fileService.getFileEntityFromFile(
                        sourceFolderEntity.getRootFolderId(),
                        sourceFolderEntity.getRootFolderFullPath(),
                        file
                );
                List<FileEventEntity> sourceFileEventList = fileEventService.getByFileEventTypeAndFileId(
                        FileEventTypeEnum.FILE_CHANGED,
                        sourceFileEntity.getFileId());
                assert CollectionUtils.isNotEmpty(sourceFileEventList);
                List<FileEventEntity> contentFileEventList = this.fileEventService.getByFileTypeAndRootFolderId(
                        FileEventTypeEnum.FILE_CHANGED,
                        contentFolderEntity.getRootFolderId());
                assert CollectionUtils.isNotEmpty(contentFileEventList);
            } else {
                FileOperationTestUtil.writeRandomBinaryData(file);
                this.waitSingleFileHandle(sourceFolderEntity.getRootFolderId());
                FileEntity sourceFileEntity = this.fileService.getFileEntityFromFile(
                        sourceFolderEntity.getRootFolderId(),
                        sourceFolderEntity.getRootFolderFullPath(),
                        file
                );
                List<FileEventEntity> fileEventEntityList = fileEventService.getByFileEventTypeAndFileId(
                        FileEventTypeEnum.FILE_CHANGED,
                        sourceFileEntity.getFileId());
                assert CollectionUtils.isNotEmpty(fileEventEntityList);
                List<FileEventEntity> contentFileEventList = this.fileEventService.getByFileTypeAndRootFolderId(
                        FileEventTypeEnum.FILE_CHANGED,
                        contentFolderEntity.getRootFolderId());
                assert CollectionUtils.isNotEmpty(contentFileEventList);
            }
        }
    }

    @Test
    void shouldReturnTrueWhenSourceFolderCreateFile() throws SyncDuoException, IOException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        // Add a sleep to allow time for handler handling
        this.waitAllFileHandle();
        // manual add file to source folder
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        Path txtFile = txtAndBinFile.getLeft();
        Path binFile = txtAndBinFile.getRight();
        // source folder event handle check
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderFullPath(sourceFolderPath);
        // Add a sleep to allow time for handler
        this.waitSingleFileHandle(sourceFolderEntity.getRootFolderId());
        // source folder event handle check
        FileEntity txtFileEntity = this.fileService.getFileEntityFromFile(
                sourceFolderEntity.getRootFolderId(),
                sourceFolderPath,
                txtFile);
        FileEntity binFileEntity = this.fileService.getFileEntityFromFile(
                sourceFolderEntity.getRootFolderId(),
                sourceFolderPath,
                binFile);
        assert ObjectUtils.allNotNull(txtFileEntity, binFileEntity);
        // internal folder handle check
        RootFolderEntity internalFolderEntity = this.rootFolderService.getByFolderFullPath(internalFolderPath);
        FileEntity txtFileEntity2 = this.fileService.getInternalFileEntityFromSourceEntity(
                internalFolderEntity.getRootFolderId(),
                txtFileEntity
        );
        FileEntity binFileEntity2 = this.fileService.getInternalFileEntityFromSourceEntity(
                internalFolderEntity.getRootFolderId(),
                binFileEntity
        );
        assert ObjectUtils.allNotNull(txtFileEntity2, binFileEntity2);
        // content folder handle check
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        FileEntity txtFileEntity3 = this.fileService.getInternalFileEntityFromSourceEntity(
                contentFolderEntity.getRootFolderId(),
                txtFileEntity2
        );
        FileEntity binFileEntity3 = this.fileService.getInternalFileEntityFromSourceEntity(
                contentFolderEntity.getRootFolderId(),
                binFileEntity2
        );
        assert ObjectUtils.allNotNull(txtFileEntity3, binFileEntity3);
    }

    @Test
    void shouldReturn200WhenCreateAndDeleteSyncFlow() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestParentFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert  syncFlowResponse.getCode().equals(200);

        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSource2InternalSyncFlowId(syncFlowResponse.getData().get(0));
        deleteSyncFlowRequest.setInternal2ContentSyncFlowId(syncFlowResponse.getData().get(1));
        SyncFlowResponse syncFlowResponse1 = syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);

        assert  syncFlowResponse1.getCode().equals(200);
    }

    void waitAllFileHandle() {
        try {
            Thread.sleep(1000 * 6);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void waitSingleFileHandle(Long rootFolderId) throws SyncDuoException {
        this.rootFolderEventProducer.manualCheckFolder(rootFolderId);
        try {
            Thread.sleep(1000 * 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void prepareEnvironment() throws IOException {
        this.cleanUp();
        // create folder
        FileOperationTestUtil.createFolders(
                sourceFolderPath,
                4,
                3
        );
        log.info("initial finish");
    }

    void cleanUp() {
        // truncate database
        this.truncateAllTable();
        // delete folder
        try {
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(sourceFolderPath));
        } catch (IOException e) {
            log.warn("删除文件夹失败.", e);
        }
        try {
            // 删除 source folder 全部内容, 但是不包括 source folder 本身
            FileOperationUtils.deleteFolder(Path.of(internalFolderPath));
        } catch (SyncDuoException e) {
            log.error("删除文件夹失败.", e);
        }
        try {
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(contentFolderParentPath));
        } catch (IOException e) {
            log.error("删除文件夹失败.", e);
        }
    }

    void truncateAllTable() {
        // root folder truncate
        List<RootFolderEntity> dbResult = this.rootFolderService.list();
        if (CollectionUtils.isNotEmpty(dbResult)) {
            this.rootFolderService.
                    removeBatchByIds(
                            dbResult.stream().map(RootFolderEntity::getRootFolderId).collect(Collectors.toList()));
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
        log.info("truncate all table");
    }
}
