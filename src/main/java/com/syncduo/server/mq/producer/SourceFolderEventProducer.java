package com.syncduo.server.mq.producer;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
                try {
                    systemQueue.sendFileEvent(
                            file.toPath(),
                            folderId,
                            FileEventTypeEnum.FILE_CREATED,
                            RootFolderTypeEnum.SOURCE_FOLDER);
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }

            @Override
            public void onFileDelete(File file) {
                try {
                    systemQueue.sendFileEvent(
                            file.toPath(),
                            folderId,
                            FileEventTypeEnum.FILE_CHANGED,
                            RootFolderTypeEnum.SOURCE_FOLDER);
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }

            @Override
            public void onFileChange(File file) {
                try {
                    systemQueue.sendFileEvent(
                            file.toPath(),
                            folderId,
                            FileEventTypeEnum.FILE_DELETED,
                            RootFolderTypeEnum.SOURCE_FOLDER);
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }
        });
        monitor.addObserver(observer);
        try {
            monitor.start();
        } catch (Exception e) {
            throw new SyncDuoException("启动源文件夹 monitor 失败", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void systemStartUp() throws SyncDuoException {

    }
}