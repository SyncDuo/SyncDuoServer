package com.syncduo.server.mq.producer;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;

@Slf4j
@Service
public class SourceFolderEventProducer {

    @Value("${syncduo.server.event.polling.interval:10000}")
    private Integer interval;

    private final SystemQueue systemQueue;

    @Autowired
    public SourceFolderEventProducer(SystemQueue systemQueue) {
        this.systemQueue = systemQueue;
    }

    public void addWatcher(String folderPath, Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("创建 watcher 失败, folderId 为空");
        }

        Path folder = FileOperationUtils.isFolderPathValid(folderPath);
        long pollingInterval = interval;

        FileAlterationObserver observer =
                new FileAlterationObserver(folder.toFile(), FileFilterUtils.fileFileFilter());
        FileAlterationMonitor monitor = new FileAlterationMonitor(pollingInterval);

        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                sourceEventSend(file, systemQueue, folderId, FileEventTypeEnum.SOURCE_FOLDER_FILE_CREATED);
            }

            @Override
            public void onFileDelete(File file) {
                sourceEventSend(file, systemQueue, folderId, FileEventTypeEnum.SOURCE_FOLDER_FILE_DELETED);
            }

            @Override
            public void onFileChange(File file) {
                sourceEventSend(file, systemQueue, folderId, FileEventTypeEnum.SOURCE_FOLDER_FILE_CHANGED);
            }
        });
        monitor.addObserver(observer);

        try {
            monitor.start();
        } catch (Exception e) {
            throw new SyncDuoException("启动源文件夹 monitor 失败", e);
        }
    }

    private static void sourceEventSend(
            File file, SystemQueue systemQueue, Long folderId, FileEventTypeEnum fileEventType) {
        Path nioFile = file.toPath();

        FileEventDto fileEvent = new FileEventDto();
        fileEvent.setFile(nioFile);
        fileEvent.setRootFolderId(folderId);
        fileEvent.setFileEventType(fileEventType);

        systemQueue.pushSourceEvent(fileEvent);
    }
}