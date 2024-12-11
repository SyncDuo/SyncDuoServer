package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.syncduo.server.enums.*;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.mq.producer.RootFolderEventProducer;
import com.syncduo.server.util.FileOperationUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class AdvancedFileOpService {
    private final FileService fileService;

    private final SystemQueue systemQueue;

    private final RootFolderService rootFolderService;

    private final SyncFlowService syncFlowService;

    private final SyncSettingService syncSettingService;

    private final RootFolderEventProducer rootFolderEventProducer;

    @Autowired
    public AdvancedFileOpService(
            FileService fileService,
            SystemQueue systemQueue,
            RootFolderService rootFolderService,
            SyncFlowService syncFlowService,
            SyncSettingService syncSettingService,
            RootFolderEventProducer rootFolderEventProducer) {
        this.fileService = fileService;
        this.systemQueue = systemQueue;
        this.rootFolderService = rootFolderService;
        this.syncFlowService = syncFlowService;
        this.syncSettingService = syncSettingService;
        this.rootFolderEventProducer = rootFolderEventProducer;
    }

    @Scheduled(fixedDelayString = "${syncduo.server.check.folder.insync.interval:1800000}")
    public void checkFolderInSync() {
        try {
            List<SyncFlowEntity> syncFlowEntityList =
                    this.syncFlowService.getBySyncFlowStatus(SyncFlowStatusEnum.SYNC);
            if (CollectionUtils.isEmpty(syncFlowEntityList)) {
                return;
            }
            // Get current time minus 5 minutes
            Timestamp fiveMinutesAgo = Timestamp.from(Instant.now().minusSeconds(5 * 60));
            for (SyncFlowEntity syncFlowEntity : syncFlowEntityList) {
                // 检查 lastSyncTime 是否为空
                if (ObjectUtils.isEmpty(syncFlowEntity.getLastSyncTime())) {
                    throw new SyncDuoException("LastSyncTime is empty");
                }
                // 五分钟内没有修改, 则检查同步情况
                if (syncFlowEntity.getLastSyncTime().before(fiveMinutesAgo)) {
                    boolean isSynced = this.isSyncFlowSync(syncFlowEntity);
                    log.info("sync-flow {} sync status: {}", syncFlowEntity, isSynced);
                }
            }
        } catch (SyncDuoException e) {
            log.error("checkFolderInSync 失败", e);
        }
    }

    public void systemStartUp() throws SyncDuoException {
        List<SyncFlowEntity> allSyncFlowList = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlowList)) {
            log.info("systemStartUp 没有未删除的 sync-flow");
            return;
        }
        for (SyncFlowEntity syncFlowEntity : allSyncFlowList) {
            boolean isSynced = this.isSyncFlowSync(syncFlowEntity);
            // 如果是 inSynced, 则添加 watcher
            if (isSynced) {
                // 获取 sync Flow Entity 的 type
                SyncFlowTypeEnum syncFlowType = SyncFlowTypeEnum.valueOf(syncFlowEntity.getSyncFlowType());
                if (ObjectUtils.isEmpty(syncFlowType)) {
                    throw new SyncDuoException("SyncFlowType is empty");
                }
                Long folderIdToAddWatcher;
                switch (syncFlowType) {
                    case SOURCE_TO_INTERNAL -> folderIdToAddWatcher = syncFlowEntity.getSourceFolderId();
                    case INTERNAL_TO_CONTENT -> folderIdToAddWatcher = syncFlowEntity.getDestFolderId();
                    default -> throw new SyncDuoException("不支持的 sync-flow type. " + syncFlowType);
                }
                RootFolderEntity rootFolderEntity = this.rootFolderService.getByFolderId(folderIdToAddWatcher);
                rootFolderEventProducer.addWatcher(rootFolderEntity);
            }
            // todo: 如果不是 inSynced, 怎么在文件事件处理完了之后添加 watcher?
            log.info("sync-flow {} sync status: {}", syncFlowEntity, isSynced);
        }
    }

    public void initialScan(RootFolderEntity rootFolder) throws SyncDuoException {
        // 检查参数
        RootFolderTypeEnum rootFolderType = RootFolderTypeEnum.valueOf(rootFolder.getRootFolderType());
        if (ObjectUtils.isEmpty(rootFolderType) || rootFolderType.equals(RootFolderTypeEnum.INTERNAL_FOLDER)) {
            throw new SyncDuoException("rootFolderType %s 不支持".formatted(rootFolderType));
        }
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    systemQueue.sendFileEvent(FileEventDto.builder()
                            .file(file)
                            .rootFolderId(rootFolder.getRootFolderId())
                            .fileEventTypeEnum(FileEventTypeEnum.FILE_CREATED)
                            .rootFolderTypeEnum(rootFolderType)
                            .destFolderTypeEnum(rootFolderType)
                            .build());
                } catch (SyncDuoException e) {
                    log.error("发送文件事件失败", e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isSyncFlowSync(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        // 获取 sync Flow Entity 的 type
        SyncFlowTypeEnum syncFlowType = SyncFlowTypeEnum.valueOf(syncFlowEntity.getSyncFlowType());
        if (ObjectUtils.isEmpty(syncFlowType)) {
            throw new SyncDuoException("SyncFlowType is empty");
        }
        // 获取 source 和 dest 的 rootFolderEntity
        RootFolderEntity sourceFolderEntity =
                this.rootFolderService.getByFolderId(syncFlowEntity.getSourceFolderId());
        RootFolderEntity destFolderEntity =
                this.rootFolderService.getByFolderId(syncFlowEntity.getDestFolderId());
        // 执行 full scan
        boolean isSourceFolderSync = this.fullScan(sourceFolderEntity);
        boolean isDestFolderSync = this.fullScan(destFolderEntity);
        boolean isSynced = true;
        // 根据 full scan 的结果更新 isSynced
        // 并且判断是否需要执行 compare
        if (isSourceFolderSync && isDestFolderSync) {
            switch (syncFlowType) {
                case SOURCE_TO_INTERNAL -> isSynced = this.isSource2InternalSyncFlowSynced(syncFlowEntity);
                case INTERNAL_TO_CONTENT -> isSynced = this.isInternal2ContentSyncFlowSync(syncFlowEntity);
                default -> throw new SyncDuoException("不支持的 SyncFlowType is " + syncFlowType);
            }
        }
        if (isSynced) {
            // isSynced 的情况也要更新数据库, 是因为需要更新 lastSyncedTime 字段, 减少 checkFolderInSync 的频率
            this.syncFlowService.updateSyncFlowStatus(
                    syncFlowEntity,
                    SyncFlowStatusEnum.SYNC
            );
        } else {
            this.syncFlowService.updateSyncFlowStatus(
                    syncFlowEntity,
                    SyncFlowStatusEnum.NOT_SYNC
            );
        }
        return isSynced;
    }

    public boolean fullScan(RootFolderEntity rootFolder) throws SyncDuoException {
        final boolean[] isSync = {true};
        // 检查参数
        Long rootFolderId = rootFolder.getRootFolderId();
        RootFolderTypeEnum rootFolderType = RootFolderTypeEnum.valueOf(rootFolder.getRootFolderType());
        if (ObjectUtils.isEmpty(rootFolderType)) {
            throw new SyncDuoException("无法进行 full scan, rootFolderType 为空");
        }
        // internal folder 不进行 full scan, 因为对于用户来说不可见, 也不允许修改
        // 对于 internal folder 和 source folder desync 的情况
        // 1. source folder 新增文件, internal folder 还没更新, 此时宕机, 且宕机的时候 source folder 新增的文件删除了
        //    那么 internal folder 就没有这批新增的文件, 但显然也无法通过 full scan 补充
        // 所以返回 true, 即 internal folder 没有同步的文件, 无法通过 full scan 补充
        if (rootFolderType.equals(RootFolderTypeEnum.INTERNAL_FOLDER)) {
            return true;
        }
        // 创建<uuid4, fileEntity> 的 set
        HashMap<String, FileEntity> uuid4FileEntityMap = new HashMap<>(1000);
        // 根据 rootFolderId 分页查询全部文件, 填充 <uuid4, fileEntity> 的 set
        this.pageHelper(
                ((startPage, pageSize) ->
                        this.fileService.getByRootFolderIdPaged(rootFolderId, startPage, pageSize)),
                (fileEntity) -> uuid4FileEntityMap.put(fileEntity.getFileUuid4(), fileEntity)
        );
        // 遍历文件夹, 根据 set 判断文件新增或修改
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (ObjectUtils.isEmpty(attrs)) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    // 从文件系统计算 UUID4
                    String uuid4 = FileOperationUtils.getUUID4(rootFolderId, rootFolder.getRootFolderFullPath(), file);
                    // uuid4 命中, 则判断文件是否有修改
                    if (uuid4FileEntityMap.containsKey(uuid4)) {
                        if (isFileChange(file, uuid4FileEntityMap.get(uuid4))) {
                            // 发送 file changed event
                            systemQueue.sendFileEvent(FileEventDto.builder()
                                    .file(file)
                                    .rootFolderId(rootFolderId)
                                    .fileEventTypeEnum(FileEventTypeEnum.FILE_CHANGED)
                                    .rootFolderTypeEnum(rootFolderType)
                                    .destFolderTypeEnum(rootFolderType)
                                    .build());
                            isSync[0] = false;
                        }
                        // 命中了文件表, 则需要从 map 中去掉
                        uuid4FileEntityMap.remove(uuid4);
                    } else {
                        // uuid4 不存在数据库中, 说明是新增文件
                        // 发送 file created event
                        systemQueue.sendFileEvent(FileEventDto.builder()
                                .file(file)
                                .rootFolderId(rootFolderId)
                                .fileEventTypeEnum(FileEventTypeEnum.FILE_CREATED)
                                .rootFolderTypeEnum(rootFolderType)
                                .destFolderTypeEnum(rootFolderType)
                                .build());
                        isSync[0] = false;
                    }
                } catch (SyncDuoException e) {
                    log.error("遍历文件失败 {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // set 中剩下的即为已经从文件系统中删除了, 因为没有命中过
        if (MapUtils.isEmpty(uuid4FileEntityMap)) {
            return isSync[0];
        }
        // 数据库更新, full scan 产生的文件删除不需要发送 file event
        this.fileService.deleteBatchByFileEntity(uuid4FileEntityMap.values().stream().toList());
        return isSync[0];
    }

    public boolean isSource2InternalSyncFlowSynced(SyncFlowEntity syncFlow) throws SyncDuoException {
        // 设置返回结果为 true
        AtomicBoolean isSync = new AtomicBoolean(true);
        // 检查 sync-flow 是否合法
        SyncFlowTypeEnum syncFlowType = SyncFlowTypeEnum.valueOf(syncFlow.getSyncFlowType());
        if (syncFlowType != SyncFlowTypeEnum.SOURCE_TO_INTERNAL) {
            throw new SyncDuoException("sync flow type %s 不是 source->internal".formatted(syncFlowType));
        }
        // 根据 syncFlow 获得 source folder entity
        Long sourceFolderId = syncFlow.getSourceFolderId();
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderId(sourceFolderId);
        // 根据 syncFlow 获得 internal folder entity
        Long internalFolderId = syncFlow.getDestFolderId();
        RootFolderEntity internalFolderEntity = this.rootFolderService.getByFolderId(internalFolderId);
        // page helper 查询 folder 下所有的 file, 并执行 compare 操作
        this.pageHelper(
                (startPage, pageSize) ->
                        this.fileService.getByRootFolderIdPaged(sourceFolderId, startPage, pageSize),
                (sourceFileEntity) -> {
                    // 获取 source file
                    Path sourceFile = this.fileService.getFileFromFileEntity(
                            sourceFolderEntity.getRootFolderFullPath(),
                            sourceFileEntity
                    );
                    // 找到右集
                    FileEntity internalFileEntity = this.fileService.getInternalFileEntityFromSourceEntity(
                            internalFolderId,
                            sourceFileEntity
                    );
                    if (ObjectUtils.isEmpty(internalFileEntity)) {
                        // 遍历左集,找不到对应的右集, 则新增;
                        this.systemQueue.sendFileEvent(
                                FileEventDto.builder()
                                        .file(sourceFile)
                                        .rootFolderId(sourceFolderId)
                                        .fileEventTypeEnum(FileEventTypeEnum.FILE_CREATED)
                                        .rootFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                                        .destFolderTypeEnum(RootFolderTypeEnum.INTERNAL_FOLDER)
                                        .build()
                        );
                        isSync.set(false);
                    } else if (isFileEntityChange(sourceFileEntity, internalFileEntity)) {
                        // md5checksum 或 lastModifiedTime 不一致,则更新
                        this.systemQueue.sendFileEvent(
                                FileEventDto.builder()
                                        .file(sourceFile)
                                        .rootFolderId(sourceFolderId)
                                        .fileEventTypeEnum(FileEventTypeEnum.FILE_CHANGED)
                                        .rootFolderTypeEnum(RootFolderTypeEnum.SOURCE_FOLDER)
                                        .destFolderTypeEnum(RootFolderTypeEnum.INTERNAL_FOLDER)
                                        .build()
                        );
                        isSync.set(false);
                    }
                }
        );
        return isSync.get();
    }

    public boolean isInternal2ContentSyncFlowSync(SyncFlowEntity syncFlow) throws SyncDuoException {
        // 设置返回结果为 true
        AtomicBoolean isSync = new AtomicBoolean(true);
        // 检查 sync-flow 是否合法
        SyncFlowTypeEnum syncFlowType = SyncFlowTypeEnum.valueOf(syncFlow.getSyncFlowType());
        if (syncFlowType != SyncFlowTypeEnum.INTERNAL_TO_CONTENT) {
            throw new SyncDuoException("sync flow type %s 不是 source->internal".formatted(syncFlowType));
        }
        // 根据 syncFlow 获得 internal folder entity
        Long internalFolderId = syncFlow.getSourceFolderId();
        RootFolderEntity internalFolderEntity = this.rootFolderService.getByFolderId(internalFolderId);
        // 根据 syncFlow 获得 content folder Id
        Long contentFolderId = syncFlow.getDestFolderId();
        // 根据 syncFlow 获得 sync setting entity
        SyncSettingEntity syncSettingEntity = this.syncSettingService.getBySyncFlowId(syncFlow.getSyncFlowId());
        SyncSettingEnum syncSetting = SyncSettingEnum.getByCode(syncSettingEntity.getFlattenFolder());
        if (ObjectUtils.isEmpty(syncSetting)) {
            throw new SyncDuoException("找不到对应的 sync setting");
        }
        // page helper 查询 folder 下所有的 file, 并执行 compare 操作
        this.pageHelper(
                (startPage, pageSize) ->
                        this.fileService.getByRootFolderIdPaged(internalFolderId, startPage, pageSize),
                (internalFileEntity) -> {
                    // 获取 internal file
                    Path internalFile = this.fileService.getFileFromFileEntity(
                            internalFolderEntity.getRootFolderFullPath(),
                            internalFileEntity
                    );
                    // 查询是否有 desynced 的, 所以 ignored delete
                    FileEntity contentFileEntity =
                            this.fileService.getContentFileEntityFromInternalEntityIgnoreDeleted(
                                    contentFolderId,
                                    internalFileEntity,
                                    syncSetting
                    );
                    if (ObjectUtils.isEmpty(contentFileEntity)) {
                        // 找不到, 说明确实没有同步, 则发送 file created event
                        this.systemQueue.sendFileEvent(
                                FileEventDto.builder()
                                        .file(internalFile)
                                        .rootFolderId(internalFolderId)
                                        .fileEventTypeEnum(FileEventTypeEnum.FILE_CREATED)
                                        .rootFolderTypeEnum(RootFolderTypeEnum.INTERNAL_FOLDER)
                                        .destFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                                        .build()
                        );
                        isSync.set(false);
                        return;
                    }
                    int code = this.isContentFileDesynced(contentFileEntity, internalFileEntity);
                    // return 0 代表没有改变, 1 代表 changed, 2 代表已经 desynced, 3 代表需要 desynced
                    switch (code) {
                        case 1 -> {
                            this.systemQueue.sendFileEvent(
                                FileEventDto.builder()
                                        .file(internalFile)
                                        .rootFolderId(internalFolderId)
                                        .fileEventTypeEnum(FileEventTypeEnum.FILE_CHANGED)
                                        .rootFolderTypeEnum(RootFolderTypeEnum.INTERNAL_FOLDER)
                                        .destFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                                        .build());
                            isSync.set(false);
                        }
                        case 3 -> this.systemQueue.sendFileEvent(
                                FileEventDto.builder()
                                        .file(internalFile)
                                        .rootFolderId(internalFolderId)
                                        .fileEventTypeEnum(FileEventTypeEnum.FILE_DESYNCED)
                                        .rootFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                                        .destFolderTypeEnum(RootFolderTypeEnum.CONTENT_FOLDER)
                                        .build()
                        );
                    }
                }
        );
        return isSync.get();
    }

    private boolean isFileChange(Path file, FileEntity fileEntity) throws SyncDuoException {
        // 比较 modified time 即可知道是否同一个文件在文件系统和在数据库是否一致
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        Timestamp fileLastModifiedTime = fileCrTimeAndMTime.getRight();
        Timestamp fileLastModifiedTimeDb = fileEntity.getFileLastModifiedTime();
        // truncate to seconds
        fileLastModifiedTime = new Timestamp(fileLastModifiedTime.getTime() / 1000 * 1000);
        fileLastModifiedTimeDb = new Timestamp(fileLastModifiedTimeDb.getTime() / 1000 * 1000);
        return fileLastModifiedTime.compareTo(fileLastModifiedTimeDb) > 0;
    }

    private boolean isFileEntityChange(FileEntity internalFileEntity, FileEntity contentFileEntity) {
        // lastModifiedTime 不一致,则有修改
        if (internalFileEntity.getFileLastModifiedTime().compareTo(contentFileEntity.getFileLastModifiedTime()) > 0) {
            return true;
        }
        // md5checksum 不一致,则有修改
        return !internalFileEntity.getFileMd5Checksum().equals(contentFileEntity.getFileMd5Checksum());
    }

    // return 0 代表没有改变, 1 代表 changed, 2 代表已经 desynced, 3 代表需要 desynced
    private int isContentFileDesynced(FileEntity contentFileEntity, FileEntity internalFileEntity) {
        if (contentFileEntity.getFileDesync().equals(FileDesyncEnum.FILE_SYNC.getCode())) {
            // 判断是否应该 desynced
            // content file entity 在左边
            // 表示 content file lastModifiedTime 新于 internal file
            // 则 content file 在文件系统被修改, 则 content file deSynced from internal file
            // 则说明需要 desynced
            if (contentFileEntity.getFileLastModifiedTime()
                    .compareTo(internalFileEntity.getFileLastModifiedTime()) > 0) {
                if (contentFileEntity.getFileMd5Checksum().equals(internalFileEntity.getFileMd5Checksum())) {
                    // content file 的修改时间是会晚于 internal file
                    // 因为 internal file create event 从发出到处理完成, 会有时间间隔, 取决于系统的繁忙程度
                    // 所以这里会对 checksum 比较
                    return 0;
                }
                return 3;
            } else if (isFileEntityChange(internalFileEntity, contentFileEntity)){
                // 不需要 desynced, 且 internal file 有更新, 则代表 changed
                return 1;
            } else {
                // 不需要 desynced, 且 internal file 没有更新, 则代表没有改变
                return 0;
            }
        } else {
            // 说明 desynced
            return 2;
        }
    }

    // pageHelper, boolean 表示两者是否同步
    private void pageHelper(
            PageQuery<FileEntity> pageQuery,
            ThrowingConsumer<FileEntity> throwingConsumer) throws SyncDuoException {
        // 第一次分页查询
        IPage<FileEntity> dbResultPaged = pageQuery.query(1L, 100L);
        // source folder 文件表为空,说明还没有文件需要同步,则返回 true
        if (dbResultPaged.getTotal() <= 0) {
            return;
        }
        long pages = dbResultPaged.getPages();
        for (long i = 1L; i < pages + 1;) {
            List<FileEntity> sourceFileEntityList = dbResultPaged.getRecords();
            for (FileEntity sourceFileEntity : sourceFileEntityList) {
                throwingConsumer.accept(sourceFileEntity);
            }
            // 分页查询
            i++;
            dbResultPaged = pageQuery.query(i, 100L);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws SyncDuoException;
    }

    @FunctionalInterface
    private interface PageQuery<T> {
        IPage<T> query(long startPage, long pageSize) throws SyncDuoException;
    }
}
