package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.*;
import com.syncduo.server.service.impl.*;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final FileService fileService;

    private final SyncFlowService syncFlowService;

    private final AdvancedFileOpService advancedFileOpService;

    private final RootFolderService rootFolderService;

    private final FileEventService fileEventService;

    private final SyncSettingService syncSettingService;

    private final FileOperationService fileOperationService;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    private static final String testParentPath =
            "/home/nopepsi-dev/IdeaProject/SyncDuoServer/src/test/resources";

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            FileService fileService,
            SyncFlowService syncFlowService,
            AdvancedFileOpService advancedFileOpService,
            RootFolderService rootFolderService,
            FileEventService fileEventService,
            SyncSettingService syncSettingService,
            FileOperationService fileOperationService) {
        this.syncFlowController = syncFlowController;
        this.fileService = fileService;
        this.syncFlowService = syncFlowService;
        this.advancedFileOpService = advancedFileOpService;
        this.rootFolderService = rootFolderService;
        this.fileEventService = fileEventService;
        this.syncSettingService = syncSettingService;
        this.fileOperationService = fileOperationService;
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
        // file operation truncate
        List<FileOperationEntity> fileOperationEntities = this.fileOperationService.list();
        if (CollectionUtils.isNotEmpty(fileOperationEntities)) {
            this.fileOperationService.removeBatchByIds(
                    fileOperationEntities.stream()
                            .map(FileOperationEntity::getFileOperationId)
                            .collect(Collectors.toList())
            );
        }
        log.info("truncate all table");
    }

    void prepareTestEnvironment(
            String sourceFolderPath,
            String internalFolderPath,
            String contentFolderPath,
            Runnable function)
            throws IOException, SyncDuoException {
        // truncate database
        this.truncateAllTable();
        // delete folder
        try {
            // 删除 source folder 全部内容, 但是不包括 source folder 本身
            FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(sourceFolderPath));
            FileOperationUtils.deleteFolder(Path.of(internalFolderPath));
            FileOperationUtils.deleteFolder(Path.of(contentFolderPath));
        } catch (SyncDuoException e) {
            log.error("删除文件夹失败.", e);
        }

        // create folder
        FileOperationTestUtil.createFolders(
                sourceFolderPath,
                4,
                3
        );
        try {
            function.run();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
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
    void testSyncFlowController() throws IOException, SyncDuoException {
        String sourceFolder = testParentPath + "/" + "sourceFolder";
        String internalFolder = testParentPath + "/" + ".sourceFolder";
        String contentParentFolder = testParentPath + "/" + "contentParentFolder";
        String contentFolder = contentParentFolder + "/" + "sourceFolder";
        prepareTestEnvironment(
                sourceFolder,
                internalFolder,
                contentFolder,
                () -> {
            SyncFlowRequest syncFlowRequest = new SyncFlowRequest();
            syncFlowRequest.setSourceFolderFullPath(sourceFolder);
            syncFlowRequest.setDestFolderFullPath(contentParentFolder);
            syncFlowRequest.setConcatDestFolderPath(true);
            syncFlowRequest.setFlattenFolder(false);
            SyncFlowResponse syncFlowResponse = syncFlowController.addSource2ContentSyncFlow(syncFlowRequest);
            log.info(syncFlowResponse.toString());
        });
        // Add a sleep to allow time for manual break-point inspection
        try {
            Thread.sleep(1000 * 60);  // 1000 millisecond * sec
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
