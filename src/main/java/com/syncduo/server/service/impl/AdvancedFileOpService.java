package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.util.*;

@Service
@Slf4j
public class AdvancedFileOpService {
    private final FileService fileService;

    private final SystemQueue systemQueue;

    private final RootFolderService rootFolderService;

    @Autowired
    public AdvancedFileOpService(
            FileService fileService,
            SystemQueue systemQueue, RootFolderService rootFolderService) {
        this.fileService = fileService;
        this.systemQueue = systemQueue;
        this.rootFolderService = rootFolderService;
    }

    public boolean initialScan(RootFolderEntity rootFolder) throws SyncDuoException {
        final boolean[] isSync = {true};
        // 检查参数
        RootFolderTypeEnum rootFolderType = RootFolderTypeEnum.valueOf(rootFolder.getRootFolderType());
        if (ObjectUtils.isEmpty(rootFolderType)) {
            throw new SyncDuoException("rootFolderType %s 不支持".formatted(rootFolderType));
        }
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                isSync[0] = false;
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

        return isSync[0];
    }

    public boolean fullScan(RootFolderEntity rootFolder) throws SyncDuoException {
        final boolean[] isSync = {true};
        // 检查参数
        Long rootFolderId = rootFolder.getRootFolderId();
        RootFolderTypeEnum rootFolderType = RootFolderTypeEnum.valueOf(rootFolder.getRootFolderType());
        if (ObjectUtils.isEmpty(rootFolderType)) {
            throw new SyncDuoException("无法进行 full scan, rootFolderType %s 不支持".formatted(rootFolderType));
        }
        // 根据 rootFolderId 分页查询全部文件
        long startPage = 1L;
        long pageSize = 100L;
        IPage<FileEntity> dbResultPaged =
                this.fileService.getByRootFolderIdPaged(rootFolderId, startPage, pageSize);
        // 该文件夹下没有文件, 则执行 initial scan 并返回结果
        if (dbResultPaged.getTotal() <= 0) {
            return initialScan(rootFolder);
        }
        // 创建并填充 <uuid4, fileEntity> 的 set
        HashMap<String, FileEntity> uuid4TimeStampMap = new HashMap<>((int) dbResultPaged.getTotal());
        long pages = dbResultPaged.getPages();
        for (long i = startPage; i < pages + 1; i++) {
            List<FileEntity> dbResult = dbResultPaged.getRecords();
            for (FileEntity fileEntity : dbResult) {
                uuid4TimeStampMap.put(fileEntity.getFileUuid4(), fileEntity);
            }
            dbResultPaged = this.fileService.getByRootFolderIdPaged(rootFolderId, i + 1, pageSize);
        }
        // 遍历文件夹, 根据 set 判断文件新增或修改
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    String uuid4 = FileOperationUtils.getUUID4(rootFolderId, rootFolder.getRootFolderFullPath(), file);
                    FileEventTypeEnum fileEventType;
                    // uuid4 命中, 则判断文件是否有修改
                    if (uuid4TimeStampMap.containsKey(uuid4) && isFileChange(file, uuid4TimeStampMap.get(uuid4))) {
                        // file event 标记为 changed
                        fileEventType = FileEventTypeEnum.FILE_CHANGED;
                        // 文件修改, 即为命中了文件表, 则需要从 map 中去掉
                        uuid4TimeStampMap.remove(uuid4);
                        isSync[0] = false;
                    } else {
                        // uuid4 不存在数据库中, 说明是新增文件
                        fileEventType = FileEventTypeEnum.FILE_CREATED;
                        isSync[0] = false;
                    }
                    // 发送 file event. system queue 根据 destFolderTypeEnum 分流
                    systemQueue.sendFileEvent(FileEventDto.builder()
                            .file(file)
                            .rootFolderId(rootFolderId)
                            .fileEventTypeEnum(fileEventType)
                            .rootFolderTypeEnum(rootFolderType)
                            .destFolderTypeEnum(rootFolderType)
                            .build());
                } catch (SyncDuoException e) {
                    log.error("遍历文件失败 %s".formatted(file), e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // set 中剩下的即为已经从文件系统中删除了, 因为没有命中过
        if (MapUtils.isEmpty(uuid4TimeStampMap)) {
            return isSync[0];
        }
        // 数据库更新, full scan 产生的文件删除不需要发送 file event
        this.fileService.deleteBatchByFileEntity(uuid4TimeStampMap.values().stream().toList());
        return isSync[0];
    }

    public boolean compareSource2InternalSyncFlow(SyncFlowEntity syncFlow) throws SyncDuoException {
        // 设置返回结果为 true
        boolean isSync = true;
        // 检查 sync-flow 是否合法
        SyncFlowTypeEnum syncFlowType = SyncFlowTypeEnum.valueOf(syncFlow.getSyncFlowType());
        if (syncFlowType != SyncFlowTypeEnum.SOURCE_TO_INTERNAL) {
            throw new SyncDuoException("sync flow type %s 不是 source->internal".formatted(syncFlowType));
        }
        // 根据 syncFlow 获得 source folder entity
        Long sourceFolderId = syncFlow.getSourceFolderId();
        RootFolderEntity sourceFolderEntity = this.rootFolderService.getByFolderId(sourceFolderId);
        // 根据 syncFlow 获得 internal folder entity 和 full path
        Long internalFolderId = syncFlow.getDestFolderId();
        // source file 分页查询
        long startPage = 1L;
        long pageSize = 100L;
        IPage<FileEntity> dbResultPaged =
                this.fileService.getByRootFolderIdPaged(sourceFolderId, startPage, pageSize);
        // source folder 文件表为空,说明还没有文件需要同步,则返回 true
        if (dbResultPaged.getTotal() <= 0) {
            return isSync;
        }
        long pages = dbResultPaged.getPages();
        for (long i = startPage; i < pages + 1; i++) {
            // 分页查询
            dbResultPaged = this.fileService.getByRootFolderIdPaged(sourceFolderId, i + 1, pageSize);
        }
        return isSync;
    }

    public void compareInternal2ContentSyncFlow(SyncFlowEntity syncFlow) throws SyncDuoException {

    }

    private boolean isFileChange(Path file, FileEntity fileEntity) throws SyncDuoException {
        // 比较 modified time 即可知道是否同一个文件在文件系统和在数据库是否一致
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        Timestamp fileLastModifiedTime = fileEntity.getFileLastModifiedTime();
        return fileCrTimeAndMTime.getRight().compareTo(fileLastModifiedTime) > 0;
    }
}
