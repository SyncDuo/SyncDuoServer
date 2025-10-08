package com.syncduo.server.bus;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.internal.FilesystemEvent;
import com.syncduo.server.util.FilesystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class FolderWatcher implements DisposableBean {

    @Value("${syncduo.server.system.folderWatcherIntervalMillis:5000}")
    private int interval;

    @Getter
    private final BlockingQueue<FilesystemEvent> filesystemEventQueue = new LinkedBlockingQueue<>(1000);

    // <folderPath, monitor>
    private final ConcurrentHashMap<String, FileAlterationMonitor> monitorMap =
            new ConcurrentHashMap<>(100);

    public void addWatcher(String folderPath) throws ValidationException, BusinessException {
        if (StringUtils.isBlank(folderPath)) {
            throw new ValidationException("addWatcher failed. folderPath is empty");
        }
        Path folderPathValid = FilesystemUtil.isFolderPathValid(folderPath);
        if (monitorMap.containsKey(folderPath)) {
            return;
        }
        // 创建 observer// 监听文件创建/修改/删除
        FileAlterationObserver observer = this.createObserver(folderPathValid);
        try {
            observer.initialize();
        } catch (Exception e) {
            throw new BusinessException(("addWatcher failed. observer initialize failed. " +
                    "folderPath is %s").formatted(folderPath), e);
        }
        FileAlterationMonitor monitor = new FileAlterationMonitor(getRandomInterval(interval), observer);
        try {
            monitor.start();
            this.monitorMap.put(folderPath, monitor);
        } catch (Exception e) {
            throw new BusinessException("addWatcher failed. folderPath is %s".formatted(folderPath), e);
        }
    }

    public void manualCheckFolder(String folderPath) {
        if (StringUtils.isBlank(folderPath)) {
            return;
        }
        FileAlterationMonitor monitor = this.monitorMap.get(folderPath);
        if (ObjectUtils.isEmpty(monitor)) {
            return;
        }
        monitor.getObservers().forEach(FileAlterationObserver::checkAndNotify);
    }

    public void stopMonitor(String folderPath) {
        if (StringUtils.isBlank(folderPath)) {
            return;
        }
        FileAlterationMonitor fileAlterationMonitor = this.monitorMap.get(folderPath);
        if (ObjectUtils.isEmpty(fileAlterationMonitor)) {
            return;
        }
        try {
            fileAlterationMonitor.stop();
            this.monitorMap.remove(folderPath);
        } catch (Exception e) {
            log.warn("stopWatcher failed. monitor is {}, folderPath is {}",
                    fileAlterationMonitor,
                    folderPath,
                    new BusinessException(("stopWatcher failed. monitor is %s").formatted(folderPath), e));
        }
    }

    // Method to generate a random interval with a base and range
    private static long getRandomInterval(long baseInterval) {
        Random random = new Random();
        int randomAddition = 100 + random.nextInt(901); // Random number between 100 and 1000
        return baseInterval + randomAddition;
    }

    private FileAlterationObserver createObserver(Path folder) {
        FileAlterationObserver observer = new FileAlterationObserver(folder.toFile());
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                try {
                    sendFileEvent(new FilesystemEvent(folder, file.toPath(), FileEventTypeEnum.FILE_CREATED));
                } catch (Exception e) {
                    log.error("文件夹发送 file event 失败",
                            new BusinessException("observer onFileCreate failed.", e));
                }
            }

            @Override
            public void onFileDelete(File file) {
                try {
                    sendFileEvent(new FilesystemEvent(folder, file.toPath(), FileEventTypeEnum.FILE_DELETED));
                } catch (Exception e) {
                    log.error("文件夹发送 file event 失败",
                            new BusinessException("observer onFileDelete failed.", e));
                }
            }

            @Override
            public void onFileChange(File file) {
                try {
                    sendFileEvent(new FilesystemEvent(folder, file.toPath(), FileEventTypeEnum.FILE_MODIFIED));
                } catch (Exception e) {
                    log.error("文件夹发送 file event 失败",
                            new BusinessException("observer onFileChange failed.", e));
                }
            }
        });
        return observer;
    }

    private void sendFileEvent(FilesystemEvent fileSystemEvent) throws BusinessException {
        log.debug("fileEvent: {}", fileSystemEvent);
        try {
            this.filesystemEventQueue.put(fileSystemEvent);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClassCastException | NullPointerException | IllegalArgumentException e) {
            throw new BusinessException("sendFileEvent failed. ", e);
        }
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
                this.stopMonitor(k);
                log.debug("shutdown monitor. folderPath is {}", k);
            } catch (Exception e) {
                log.warn("failed to shutdown monitor. folderPath is {}", k, e);
            }
        });
        this.monitorMap.clear();
    }
}