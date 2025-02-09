package com.syncduo.server.mq.producer;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
public class RootFolderEventProducer implements DisposableBean {

    @Value("${syncduo.server.event.polling.interval:5000}")
    private Integer interval;

    private final SystemQueue systemQueue;

    // <rootFolderId, monitor>
    private final ConcurrentHashMap<Long, FileAlterationMonitor> map = new ConcurrentHashMap<>(100);

    @Autowired
    public RootFolderEventProducer(SystemQueue systemQueue) {
        this.systemQueue = systemQueue;
    }

    // source folder 监听所有文件事件
    // content folder 监听文件删除事件, 其他事件通过 full scan, compare 触发
    public void addMonitor(RootFolderEntity rootFolderEntity) throws SyncDuoException {
        // 获取 root folder, 添加 observer
        Pair<Path, RootFolderTypeEnum> rootFolderAndType = isRootFolderEntityValid(rootFolderEntity);
        Path folder = rootFolderAndType.getLeft();
        // 获取 root folder id 和 root folder type
        Long rootFolderId = rootFolderEntity.getRootFolderId();
        RootFolderTypeEnum rootFolderTypeEnum = rootFolderAndType.getRight();
        // 创建 observer
        FileAlterationObserver observer;
        if (rootFolderTypeEnum.equals(RootFolderTypeEnum.SOURCE_FOLDER)) {
            observer = this.addListenerForSourceFolder(
                    rootFolderEntity,
                    folder
            );
        } else {
            observer = this.addListenerForContentFolder(
                    rootFolderEntity,
                    folder
            );
        }
        try {
            observer.initialize();
        } catch (Exception e) {
            throw new SyncDuoException(("addWatcher failed. observer initialize failed. " +
                    "rootFolderEntity is %s").formatted(rootFolderEntity), e);
        }
        FileAlterationMonitor monitor = new FileAlterationMonitor(getRandomInterval(interval), observer);
        try {
            monitor.start();
            this.map.put(rootFolderId, monitor);
        } catch (Exception e) {
            throw new SyncDuoException("addWatcher failed. rootFolderEntity is %s".formatted(rootFolderEntity), e);
        }
    }

    public void manualCheckFolder(Long rootFolderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(rootFolderId)) {
            throw new SyncDuoException("manualCheckFolder failed. rootFolderId is null");
        }
        FileAlterationMonitor monitor = this.map.get(rootFolderId);
        if (ObjectUtils.isEmpty(monitor)) {
            return;
        }
        monitor.getObservers().forEach(FileAlterationObserver::checkAndNotify);
    }

    public void stopMonitor(Long rootFolderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(rootFolderId)) {
            throw new SyncDuoException("stopWatcher failed. rootFolderId is null");
        }
        FileAlterationMonitor fileAlterationMonitor = this.map.get(rootFolderId);
        if (ObjectUtils.isNotEmpty(fileAlterationMonitor)) {
            try {
                fileAlterationMonitor.stop();
            } catch (Exception e) {
                log.warn("stopWatcher failed. monitor is {}, rootFolderId is {}",
                        fileAlterationMonitor,
                        rootFolderId,
                        e);
            }
        } else {
            log.warn("stopWatcher failed. can't find monitor with rootFolderId {}", rootFolderId);
        }
    }

    // Method to generate a random interval with a base and range
    private static long getRandomInterval(long baseInterval) {
        Random random = new Random();
        int randomAddition = 100 + random.nextInt(901); // Random number between 100 and 1000
        return baseInterval + randomAddition;
    }

    private FileAlterationObserver addListenerForSourceFolder(RootFolderEntity rootFolderEntity, Path folder) {
        Long rootFolderId = rootFolderEntity.getRootFolderId();
        FileAlterationObserver observer = new FileAlterationObserver(folder.toFile());
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                try {
                    systemQueue.sendFileEvent(FileEventDto.builder()
                            .file(file.toPath())
                            .rootFolderId(rootFolderId)
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_CREATED)
                            .rootFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                            .destFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                            .build());
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }

            @Override
            public void onFileDelete(File file) {
                try {
                    systemQueue.sendFileEvent(FileEventDto.builder()
                            .file(file.toPath())
                            .rootFolderId(rootFolderId)
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_DELETED)
                            .rootFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                            .destFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                            .build());
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }

            @Override
            public void onFileChange(File file) {
                try {
                    systemQueue.sendFileEvent(FileEventDto.builder()
                            .file(file.toPath())
                            .rootFolderId(rootFolderId)
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_CHANGED)
                            .rootFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                            .destFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                            .build());
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }
        });
        return observer;
    }

    private FileAlterationObserver addListenerForContentFolder(
            RootFolderEntity rootFolderEntity,
            Path folder) {
        Long rootFolderId = rootFolderEntity.getRootFolderId();
        FileAlterationObserver observer = new FileAlterationObserver(folder.toFile());
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileDelete(File file) {
                try {
                    systemQueue.sendFileEvent(FileEventDto.builder()
                            .file(file.toPath())
                            .rootFolderId(rootFolderId)
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_DELETED)
                            .rootFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                            .destFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                            .build());
                } catch (SyncDuoException e) {
                    log.error("source 文件夹发送 file event 失败", e);
                }
            }
        });
        return observer;
    }

    private static Pair<Path, RootFolderTypeEnum> isRootFolderEntityValid(RootFolderEntity rootFolderEntity)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderEntity)) {
            throw new SyncDuoException("isRootFolderEntityValid failed. rootFolderEntity is null");
        }
        // 只有 source 和 content folder 可以添加 watcher
        RootFolderTypeEnum rootFolderTypeEnum = RootFolderTypeEnum.getByString(rootFolderEntity.getRootFolderType());
        if (ObjectUtils.isEmpty(rootFolderTypeEnum) || rootFolderTypeEnum.equals(RootFolderTypeEnum.INTERNAL_FOLDER)) {
            throw new SyncDuoException(("isRootFolderEntityValid failed. " +
                    "illegal rootFolderTypeEnum %s").formatted(rootFolderTypeEnum));
        }
        Path folder = FileOperationUtils.isFolderPathValid(rootFolderEntity.getRootFolderFullPath());
        return new ImmutablePair<>(folder, rootFolderTypeEnum);
    }

    @Override
    public void destroy() {
        this.map.forEach((k, v) -> {
            try {
                v.stop();
                log.debug("shutdown monitor. rootFolderId is {}", k);
            } catch (Exception e) {
                log.warn("failed to shutdown monitor. rootFolder is {}", k, e);
            }
        });
    }
}