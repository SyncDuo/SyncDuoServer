package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.consumer.SourceFolderHandler;
import com.syncduo.server.service.impl.AdvancedFileOpService;
import com.syncduo.server.service.impl.RootFolderService;
import com.syncduo.server.service.impl.SyncFlowService;
import com.syncduo.server.util.FileOperationUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;

@SpringBootTest
@ActiveProfiles("test")
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final SourceFolderHandler sourceFolderHandler;

    private final SyncFlowService syncFlowService;

    private final AdvancedFileOpService advancedFileOpService;

    private final RootFolderService rootFolderService;

    private static final String testParentPath = "/home/nopepsi-lenovo-laptop/SyncDuoServer/src/test/resources";

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            SourceFolderHandler sourceFolderHandler, SyncFlowService syncFlowService,
            AdvancedFileOpService advancedFileOpService, RootFolderService rootFolderService) {
        this.syncFlowController = syncFlowController;
        this.sourceFolderHandler = sourceFolderHandler;
        this.syncFlowService = syncFlowService;
        this.advancedFileOpService = advancedFileOpService;
        this.rootFolderService = rootFolderService;
    }

    @Test
    void contextLoads() {

    }

    @Test
    void testInitialScan() throws SyncDuoException {
        RootFolderEntity sourceFolder = this.rootFolderService.getByFolderId(1L);
        this.advancedFileOpService.initialScan(sourceFolder);
        // Add a sleep to allow time for manual break-point inspection
        try {
            Thread.sleep(1000 * 20);  // 1000 millisecond * sec
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    void testSyncFlowController() {
        SyncFlowRequest syncFlowRequest = new SyncFlowRequest();
        syncFlowRequest.setSourceFolderFullPath(testParentPath + "/" + "sourceFolder");
        syncFlowRequest.setDestFolderFullPath(testParentPath + "/" + "contentWarehouse");
        syncFlowRequest.setConcatDestFolderPath(true);
        syncFlowRequest.setFlattenFolder(false);
        SyncFlowResponse syncFlowResponse = syncFlowController.addSource2ContentSyncFlow(syncFlowRequest);
        System.out.println(1);
    }
}
