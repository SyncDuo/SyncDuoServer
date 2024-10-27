package com.syncduo.server.service.impl;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.util.FileOperationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class AdvancedFileOpService {
    private final SystemQueue systemQueue;

    @Autowired
    public AdvancedFileOpService(SystemQueue systemQueue) {
        this.systemQueue = systemQueue;
    }

    public void initialScan(RootFolderEntity rootFolder) throws SyncDuoException {
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                FileEventDto fileEvent = new FileEventDto();
                fileEvent.setFile(file);
                fileEvent.setRootFolderId(rootFolder.getRootFolderId());
                fileEvent.setFileEventType(FileEventTypeEnum.SOURCE_FOLDER_INITIAL_SCAN);
                systemQueue.pushSourceEvent(fileEvent);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void fullScan() throws SyncDuoException {

    }

    public void compareSource2InternalSyncFlow(Long syncFlowId) throws SyncDuoException {

    }

    public void compareInternal2ContentSyncFlow(Long syncFlowId) throws SyncDuoException {

    }
}
