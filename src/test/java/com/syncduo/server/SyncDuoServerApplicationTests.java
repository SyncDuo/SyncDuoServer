package com.syncduo.server;

import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.model.dto.http.SyncFlowRequest;
import com.syncduo.server.model.dto.http.SyncFlowResponse;
import com.syncduo.server.mq.consumer.SourceFolderEventHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final SourceFolderEventHandler sourceFolderEventHandler;

    @Autowired
    SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            SourceFolderEventHandler sourceFolderEventHandler) {
        this.syncFlowController = syncFlowController;
        this.sourceFolderEventHandler = sourceFolderEventHandler;
    }

    @Test
    void contextLoads() {
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
