package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.mq.consumer.SourceFolderEventHandler;
import com.syncduo.server.service.impl.AdvancedFileOpService;
import com.syncduo.server.service.impl.RootFolderService;
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

    private final SourceFolderEventHandler sourceFolderEventHandler;

    private final AdvancedFileOpService advancedFileOpService;

    private final RootFolderService rootFolderService;

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            SourceFolderEventHandler sourceFolderEventHandler,
            AdvancedFileOpService advancedFileOpService, RootFolderService rootFolderService) {
        this.syncFlowController = syncFlowController;
        this.sourceFolderEventHandler = sourceFolderEventHandler;
        this.advancedFileOpService = advancedFileOpService;
        this.rootFolderService = rootFolderService;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void testFullScanMethod() throws SyncDuoException {
        Path file = Paths.get("/home/nopepsi-dev/IdeaProject/SyncDuoServer/src/test/folder/sourceFolder/test1.txt");
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(3L);
        this.advancedFileOpService.fullScan(rootFolderEntity);
    }

    @Test
    void testSyncFlowController() {
        SyncFlowRequest syncFlowRequest = new SyncFlowRequest();
        syncFlowRequest.setSourceFolderFullPath(
                "/home/nopepsi-dev/IdeaProject/SyncDuoServer/src/test/folder/sourceFolder");
        syncFlowRequest.setDestFolderFullPath(
                "/home/nopepsi-dev/IdeaProject/SyncDuoServer/src/test/folder/contentFolder"
        );
        SyncFlowResponse syncFlowResponse = syncFlowController.addSource2ContentSyncFlow(syncFlowRequest);
    }
}
