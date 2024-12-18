package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.enums.FileEventTypeEnum;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
            // 删除 source folder 全部内容, 但是不包括 source folder 本身
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(sourceFolderPath));
            FileOperationUtils.deleteFolder(Path.of(internalFolderPath));
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(contentFolderParentPath));
        } catch (SyncDuoException | IOException e) {
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

    @Test
    void testFilelockMethod() throws IOException, InterruptedException {
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(testParentPath));
        Path txtFile = txtAndBinFile.getLeft();
        Thread thread1 = new Thread(() -> {
            try {
                ReentrantReadWriteLock lock = FileOperationUtils.tryLockWithRetries(txtFile, true);
                System.out.println(Files.readAllLines(txtFile));
                Thread.sleep(1000 * 7);
                lock.readLock().unlock();
            } catch (SyncDuoException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        Thread thread2 = new Thread(() -> {
            try {
                ReentrantReadWriteLock lock = FileOperationUtils.tryLockWithRetries(txtFile, false);
                System.out.println(Files.readAllLines(txtFile));
            } catch (SyncDuoException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
    }

    @Test
    void testGetFileEntityFromFile() throws SyncDuoException {
        Path file = Path.of(testParentPath + "/.sourceFolder/TestFolder1_1/TestFolder2_1/TestFolder2_1.txt");
        FileEntity fileEntityFromFile = this.fileService.getFileEntityFromFile(
                146L,
                testParentPath + "/.sourceFolder",
                file
        );
        System.out.println(1);
    }

    @Test
    void testInternal2ContentUUID4() throws SyncDuoException {
        Path internalFile = FileOperationUtils.isFilePathValid(testParentPath + "/sourceFolder/" + "TestFolder0_1.txt");
        Path contentFile = FileOperationUtils.isFilePathValid(
                testParentPath + "/contentParentFolder/sourceFolder/" + "TestFolder0_1.txt");

        FileEntity internalFileEntity = this.fileService.fillFileEntityForCreate(
                internalFile,
                100L,
                testParentPath + "/sourceFolder"
        );
        FileEntity contentFileEntity = this.fileService.fillFileEntityForCreate(
                contentFile,
                101L,
                testParentPath + "/contentParentFolder/sourceFolder"
        );
        String uuid4 = FileOperationUtils.getUUID4(
                101L,
                FileOperationUtils.getPathSeparator(),
                internalFileEntity.getFileUuid4() + "." + internalFileEntity.getFileExtension()
        );
        assert uuid4.equals(contentFileEntity.getFileUuid4());
        log.info("1");
    }

    @Test
    void testInitialScan() throws SyncDuoException {
        RootFolderEntity sourceFolder = this.rootFolderService.getByFolderId(1L);
        RootFolderEntity content = this.rootFolderService.getByFolderId(3L);
        this.advancedFileOpService.initialScan(sourceFolder);
        this.advancedFileOpService.initialScan(content);
        // Add a sleep to allow time for manual break-point inspection
        try {
            Thread.sleep(1000 * 20);  // 1000 millisecond * sec
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testCheckFolderInSync() throws SyncDuoException {
        this.advancedFileOpService.checkFolderInSync();
    }

    @Test
    void isSource2InternalSyncFlowSynced() throws SyncDuoException {
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(1L);
        boolean result = this.advancedFileOpService.isSource2InternalSyncFlowSynced(syncFlowEntity);
        System.out.println(1);
    }

    @Test
    void isInternal2ContentSyncFlowSync() throws SyncDuoException {
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getById(2L);
        boolean result = this.advancedFileOpService.isSource2InternalSyncFlowSynced(syncFlowEntity);
        System.out.println(1);
    }

    @Test
    void testFullScanMethod() throws SyncDuoException {
        RootFolderEntity sourceFolder = this.rootFolderService.getByFolderId(1L);
        this.advancedFileOpService.fullScan(sourceFolder);
    }

    @Test
    void shouldReturnTrueWhenSourceFolderChangeFile() throws IOException, InterruptedException, SyncDuoException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setConcatDestFolderPath(true);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        // Add a sleep to allow time for handler handling
        Thread.sleep(1000 * 5);  // 1000 millisecond * sec
        // get source folder entity
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderFullPath(sourceFolderPath);
        // get content folder entity
        RootFolderEntity contentFolderEntity = this.rootFolderService.getByFolderFullPath(contentFolderPath);
        // manual change file in source folder
        List<Path> allFile = FileOperationTestUtil.getAllFile(Path.of(sourceFolderPath));
        for (Path file : allFile) {
            if (file.getFileName().toString().contains("txt")) {
                FileOperationTestUtil.writeToTextFile(file);
                Thread.sleep(1000 * 5);  // 1000 millisecond * sec
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
                Thread.sleep(1000 * 5);  // 1000 millisecond * sec
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
    void shouldReturnTrueWhenSourceFolderCreateFile() throws SyncDuoException, IOException, InterruptedException {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setConcatDestFolderPath(true);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        // Add a sleep to allow time for handler handling
        Thread.sleep(1000 * 5);  // 1000 millisecond * sec
        // manual add file to source folder
        Pair<Path, Path> txtAndBinFile = FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        Path txtFile = txtAndBinFile.getLeft();
        Path binFile = txtAndBinFile.getRight();
        // Add a sleep to allow time for handler handling
        Thread.sleep(1000 * 2);  // 1000 millisecond * sec
        // source folder event handle check
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderFullPath(sourceFolderPath);
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
    void shouldReturn200WhenDeleteSyncFlow() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setConcatDestFolderPath(true);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert  syncFlowResponse.getCode().equals(200);

        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSource2InternalSyncFlowId(syncFlowResponse.getData().get(0));
        deleteSyncFlowRequest.setInternal2ContentSyncFlowId(syncFlowResponse.getData().get(1));
        SyncFlowResponse syncFlowResponse1 = syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);

        assert  syncFlowResponse1.getCode().equals(200);
    }

    @Test
    void shouldReturn200WhenCreateSyncFlow() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderParentPath);
        createSyncFlowRequest.setConcatDestFolderPath(true);
        createSyncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert  syncFlowResponse.getCode().equals(200);
    }
}
