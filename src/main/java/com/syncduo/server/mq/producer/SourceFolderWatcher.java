package com.syncduo.server.mq.producer;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.mq.EventQueue;
import com.syncduo.server.service.impl.FileService;
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
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
@Service
public class SourceFolderWatcher {

    @Value("${syncduo.server.event.polling.interval:10000}")
    private Integer interval;

    private final EventQueue eventQueue;

    @Autowired
    public SourceFolderWatcher(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    public void addWatcher(String folderPath) throws SyncDuoException {

        Path folder = FileOperationUtils.isFolderPathValid(folderPath);
        long pollingInterval = interval;

        FileAlterationObserver observer =
                new FileAlterationObserver(folder.toFile(), FileFilterUtils.fileFileFilter());
        FileAlterationMonitor monitor = new FileAlterationMonitor(pollingInterval);

        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                sourceEventCommon(file, eventQueue, FileEventTypeEnum.SOURCE_FOLDER_FILE_CREATED);
            }

            @Override
            public void onFileDelete(File file) {
                sourceEventCommon(file, eventQueue, FileEventTypeEnum.SOURCE_FOLDER_FILE_DELETED);
            }

            @Override
            public void onFileChange(File file) {
                sourceEventCommon(file, eventQueue, FileEventTypeEnum.SOURCE_FOLDER_FILE_CHANGED);
            }
        });
        monitor.addObserver(observer);

        try {
            monitor.start();
        } catch (Exception e) {
            throw new SyncDuoException("启动源文件夹 monitor 失败", e);
        }

    }

    private static void sourceEventCommon(File file, EventQueue eventQueue, FileEventTypeEnum fileEventType) {
        Path nioFile = file.toPath();

        FileEventDto fileEvent = new FileEventDto();
        fileEvent.setFile(nioFile);
        fileEvent.setFileEventType(fileEventType);

        eventQueue.pushSourceEvent(fileEvent);
    }

    private void popularFileEventFromFile(Path file, FileEventDto fileEventDto) throws SyncDuoException {
        if (ObjectUtils.anyNull(file, fileEventDto)) {
            throw new SyncDuoException("填充 FileEventDto 失败,源文件:%s 或 FileEventDto 为空".formatted(file));
        }

        BasicFileAttributes fileBasicAttributes = FileOperationUtils.getFileBasicAttributes(file);
        fileEventDto.setBasicFileAttributes(fileBasicAttributes);

        String fileMd5Checksum = FileOperationUtils.getMD5Checksum(file);
        fileEventDto.setFileMd5Checksum(fileMd5Checksum);
    }
}