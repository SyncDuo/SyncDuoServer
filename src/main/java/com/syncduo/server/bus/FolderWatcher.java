package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.internal.FileSystemEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FolderWatcher implements DisposableBean {

    @Value("${syncduo.server.event.polling.interval:5000}")
    private Integer interval;

    private final SystemBus systemBus;

    // <rootFolderId, monitor>
    private final ConcurrentHashMap<Long, FileAlterationMonitor> monitorMap =
            new ConcurrentHashMap<>(100);

    @Autowired
    public FolderWatcher(SystemBus systemBus) {
        this.systemBus = systemBus;
    }

    public void addWatcher(FolderEntity folderEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderEntity.getFolderId())) {
            throw new SyncDuoException("addWatcher failed. folderId is null");
        }
        if (ObjectUtils.anyNull(folderEntity, folderEntity.getFolderId(), folderEntity.getFolderFullPath())) {
            throw new SyncDuoException("addWatcher failed. folderId or folderFullPath is null");
        }
        if (monitorMap.containsKey(folderEntity.getFolderId())) {
            return;
        }
        // 创建 observer
        Long folderId = folderEntity.getFolderId();
        // 监听文件创建/修改/删除
        FileAlterationObserver observer = this.createObserver(
                folderId,
                Path.of(folderEntity.getFolderFullPath())
        );
        try {
            observer.initialize();
        } catch (Exception e) {
            throw new SyncDuoException(("addWatcher failed. observer initialize failed. " +
                    "folderId is %s").formatted(folderId), e);
        }
        FileAlterationMonitor monitor = new FileAlterationMonitor(getRandomInterval(interval), observer);
        try {
            monitor.start();
            this.monitorMap.put(folderId, monitor);
        } catch (Exception e) {
            throw new SyncDuoException("addWatcher failed. folderId is %s".formatted(folderId), e);
        }
    }

    public void manualCheckFolder(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("manualCheckFolder failed. folderId is null");
        }
        FileAlterationMonitor monitor = this.monitorMap.get(folderId);
        if (ObjectUtils.isEmpty(monitor)) {
            return;
        }
        monitor.getObservers().forEach(FileAlterationObserver::checkAndNotify);
    }

    public void stopMonitor(Long folderId) {
        if (ObjectUtils.isEmpty(folderId)) {
            return;
        }
        if (!this.monitorMap.containsKey(folderId)) {
            log.warn("stopWatcher failed. can't find monitor with folderId {}", folderId);
            return;
        }
        FileAlterationMonitor fileAlterationMonitor = this.monitorMap.get(folderId);
        try {
            fileAlterationMonitor.stop();
            this.monitorMap.remove(folderId);
        } catch (Exception e) {
            log.warn("stopWatcher failed. monitor is {}, folderId is {}",
                    fileAlterationMonitor,
                    folderId,
                    e);
        }
    }

    // Method to generate a random interval with a base and range
    private static long getRandomInterval(long baseInterval) {
        Random random = new Random();
        int randomAddition = 100 + random.nextInt(901); // Random number between 100 and 1000
        return baseInterval + randomAddition;
    }

    private FileAlterationObserver createObserver(Long folderId, Path folder) {
        FileAlterationObserver observer = new FileAlterationObserver(folder.toFile());
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                try {
                    systemBus.sendFileEvent(new FileSystemEvent(
                        folderId, file.toPath(), FileEventTypeEnum.FILE_CREATED
                    ));
                } catch (SyncDuoException e) {
                    log.error("文件夹发送 file event 失败", e);
                }
            }

            @Override
            public void onFileDelete(File file) {
                try {
                    systemBus.sendFileEvent(new FileSystemEvent(
                            folderId, file.toPath(), FileEventTypeEnum.FILE_DELETED
                    ));
                } catch (SyncDuoException e) {
                    log.error("文件夹发送 file event 失败", e);
                }
            }

            @Override
            public void onFileChange(File file) {
                try {
                    systemBus.sendFileEvent(new FileSystemEvent(
                            folderId, file.toPath(), FileEventTypeEnum.FILE_CHANGED
                    ));
                } catch (SyncDuoException e) {
                    log.error("文件夹发送 file event 失败", e);
                }
            }
        });
        return observer;
    }

    public int getWatcherNumber() {
        if (MapUtils.isEmpty(this.monitorMap)) {
            return 0;
        }
        return this.monitorMap.size();
    }

    @Override
    public void destroy() {
        this.monitorMap.forEach((k, v) -> {
            try {
                v.stop();
                log.debug("shutdown monitor. rootFolderId is {}", k);
            } catch (Exception e) {
                log.warn("failed to shutdown monitor. rootFolder is {}", k, e);
            }
        });
        this.monitorMap.clear();
    }
}