package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Service
@Slf4j
public class AdvancedFileOpService {
    private final FileService fileService;

    private final SystemQueue systemQueue;

    @Autowired
    public AdvancedFileOpService(
            FileService fileService,
            SystemQueue systemQueue) {
        this.fileService = fileService;
        this.systemQueue = systemQueue;
    }

    public void initialScan(RootFolderEntity rootFolder) throws SyncDuoException {
        RootFolderTypeEnum rootFolderType = RootFolderTypeEnum.valueOf(rootFolder.getRootFolderType());
        if (ObjectUtils.isEmpty(rootFolderType)) {
            throw new SyncDuoException("rootFolderType %s 不支持".formatted(rootFolderType));
        }
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    systemQueue.sendFileEvent(
                            file,
                            rootFolder.getRootFolderId(),
                            FileEventTypeEnum.FILE_CREATED,
                            rootFolderType);
                } catch (SyncDuoException e) {
                    log.error("发送文件事件失败", e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void fullScan(RootFolderEntity rootFolder) throws SyncDuoException {
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
        if (dbResultPaged.getTotal() <= 0) {
            return;
        }
        // 创建并填充 <uuid4> 的 set
        HashSet<String> uuid4Set = new HashSet<>((int) dbResultPaged.getTotal());
        long pages = dbResultPaged.getPages();
        for (long i = startPage; i < pages + 1; i++) {
            List<FileEntity> dbResult = dbResultPaged.getRecords();
            for (FileEntity fileEntity : dbResult) {
                uuid4Set.add(fileEntity.getFileUuid4());
            }
            dbResultPaged = this.fileService.getByRootFolderIdPaged(rootFolderId, i + 1, pageSize);
        }
        // 遍历文件夹, 根据 set 判断文件新增或修改
        FileOperationUtils.walkFilesTree(rootFolder.getRootFolderFullPath(), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    String uuid4 = FileOperationUtils.getUuid4(file);
                    FileEventTypeEnum fileEventType;
                    // 文件新增
                    if (uuid4Set.contains(uuid4)) {
                        // file event 统一是 changed, 负责 meta data 更新或 checksum 更新
                        fileEventType = FileEventTypeEnum.FILE_CHANGED;
                        // 文件修改, 即为命中了文件表, 则需要从 map 中去掉
                        uuid4Set.remove(uuid4);
                    } else {
                        // uuid4 不存在数据库中, 说明是新增文件
                        fileEventType = FileEventTypeEnum.FILE_CREATED;
                    }
                    // 发送 file event
                    systemQueue.sendFileEvent(
                            file,
                            rootFolderId,
                            fileEventType,
                            rootFolderType
                    );
                } catch (SyncDuoException e) {
                    log.error("遍历文件失败 %s".formatted(file), e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // set 中剩下的即为已经从文件系统中删除了, 因为没有命中过
        if (CollectionUtils.isEmpty(uuid4Set)) {
            return;
        }
        // 数据库更新, full scan 产生的文件删除不需要发送 file event
        this.fileService.deleteBatchByUuid4s(uuid4Set.stream().toList());
    }

    public void compareSource2InternalSyncFlow(Long syncFlowId) throws SyncDuoException {

    }

    public void compareInternal2ContentSyncFlow(Long syncFlowId) throws SyncDuoException {

    }
}
