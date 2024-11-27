package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileMapper;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.service.IFileService;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class FileService extends ServiceImpl<FileMapper, FileEntity> implements IFileService {
    public void createFileRecord(FileEntity fileEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileEntity)) {
            throw new SyncDuoException("创建文件记录失败, FileEntity 为空");
        }

        boolean saved = this.save(fileEntity);
        if (!saved) {
            throw new SyncDuoException("创建文件记录失败, 无法写入数据库");
        }
    }

    public void updateFileEntityByFile(FileEntity fileEntity, Path file) throws SyncDuoException {
        if (ObjectUtils.anyNull(fileEntity, file)) {
            throw new SyncDuoException("获取file失败, fileEntity 或 file 为空");
        }
        // 更新  md5checksum,last_modified_time
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        fileEntity.setLastUpdatedTime(fileCrTimeAndMTime.getRight());
        String md5Checksum = FileOperationUtils.getMD5Checksum(file);
        fileEntity.setFileMd5Checksum(md5Checksum);
        // 更新数据库
        this.updateById(fileEntity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteBatchByFileEntity(List<FileEntity> fileEntityList) throws SyncDuoException {
        if (CollectionUtils.isEmpty(fileEntityList)) {
            return;
        }
        for (FileEntity fileEntity : fileEntityList) {
            fileEntity.setFileDeleted(DeletedEnum.DELETED.getCode());
        }
        boolean updated = this.updateBatchById(fileEntityList);
        if (!updated) {
            throw new SyncDuoException("删除失败");
        }
    }

    public FileEntity getFileEntityFromFile(Long rootFolderId, String rootFolderFullPath, Path file)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId, file)) {
            throw new SyncDuoException("rootFolderId 或 file 为空");
        }
        String uuid4 = FileOperationUtils.getUUID4(rootFolderId, rootFolderFullPath, file);
        return this.getByUuid4(uuid4);
    }

    public FileEntity getDestFileEntityFromSourceEntity(String destFolderFullPath, FileEntity sourceFileEntity)
            throws SyncDuoException {
        if (StringUtils.isBlank(destFolderFullPath)) {
            throw new SyncDuoException("获取file失败, destFolderFullPath 为空");
        }
        if (ObjectUtils.isEmpty(sourceFileEntity)) {
            throw new SyncDuoException("获取file失败, sourceFileEntity 为空");
        }
        String destFileFullPath = FileOperationUtils.concatePathString(
                destFolderFullPath,
                sourceFileEntity.getRelativePath(),
                sourceFileEntity.getFileName(),
                sourceFileEntity.getFileExtension()
        );
        String uuid4 = FileOperationUtils.getUuid4(destFileFullPath);
        return this.getByUuid4(uuid4);
    }

    public List<FileEntity> getDestFileEntityFromSourceEntityIgnoredDesynced(
            String destFolderFullPath,
            FileEntity sourceFileEntity)
            throws SyncDuoException {
        if (StringUtils.isBlank(destFolderFullPath)) {
            throw new SyncDuoException("获取file失败, destFolderFullPath 为空");
        }
        if (ObjectUtils.isEmpty(sourceFileEntity)) {
            throw new SyncDuoException("获取file失败, sourceFileEntity 为空");
        }
        String destFileFullPath = FileOperationUtils.concatePathString(
                destFolderFullPath,
                sourceFileEntity.getRelativePath(),
                sourceFileEntity.getFileName(),
                sourceFileEntity.getFileExtension()
        );
        String uuid4 = FileOperationUtils.getUuid4(destFileFullPath);
        return this.getByUuid4IgnoredDesynced(uuid4);
    }

    public Path getFileFromFileEntity(String rootFolderFullPath, FileEntity fileEntity) throws SyncDuoException {
        if (StringUtils.isBlank(rootFolderFullPath)) {
            throw new SyncDuoException("获取file失败, rootFolderFullPath 为空");
        }
        if (ObjectUtils.isEmpty(fileEntity)) {
            throw new SyncDuoException("获取file失败, fileEntity 为空");
        }
        return FileOperationUtils.concateStringToPath(
                rootFolderFullPath,
                fileEntity.getRelativePath(),
                fileEntity.getFileName(),
                fileEntity.getFileExtension()
        );
    }

    public FileEntity getByUuid4(String uuid4) throws SyncDuoException {
        if (StringUtils.isEmpty(uuid4)) {
            throw new SyncDuoException("获取文件记录失败, uuid4 为空");
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFileUuid4, uuid4);
        queryWrapper.eq(FileEntity::getFileDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<FileEntity> dbResult = this.list(queryWrapper);

        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    private List<FileEntity> getByUuid4IgnoredDesynced(String uuid4) throws SyncDuoException {
        if (StringUtils.isEmpty(uuid4)) {
            throw new SyncDuoException("获取文件记录失败, uuid4 为空");
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFileUuid4, uuid4);
        List<FileEntity> dbResult = this.list(queryWrapper);

        return CollectionUtils.isEmpty(dbResult) ? Collections.emptyList() : dbResult;
    }

    public IPage<FileEntity> getByRootFolderIdPaged(Long rootFolderId, Long page, Long pageSize)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId, page, pageSize)) {
            throw new SyncDuoException(
                    "获取文件记录失败, rootFolderId %s, Long page %s, Long pageSize %s 存在空值"
                            .formatted(rootFolderId, page, pageSize));
        }
        if (page <= 0 || pageSize <= 0 || pageSize >= 1000) {
            throw new SyncDuoException("page 或 pageSize 小于 0 或者 pageSize 大于 1000");
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getRootFolderId, rootFolderId);
        queryWrapper.eq(FileEntity::getFileDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.page(new Page<>(page, pageSize), queryWrapper);
    }

    public FileEntity fillFileEntityForCreate(
            Path file, Long rootFolderId, String rootFolderFullPath) throws SyncDuoException {
        FileEntity fileEntity = new FileEntity();

        // 获取 created_time and last_modified_time
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FileOperationUtils.getFileCrTimeAndMTime(file);
        fileEntity.setFileCreatedTime(fileCrTimeAndMTime.getLeft());
        fileEntity.setFileLastModifiedTime(fileCrTimeAndMTime.getRight());

        // 获取 md5 checksum
        String md5Checksum = FileOperationUtils.getMD5Checksum(file);
        fileEntity.setFileMd5Checksum(md5Checksum);

        // 设置 root folder id , 根据 folder full path 计算 relative path
        fileEntity.setRootFolderId(rootFolderId);
        String relativePath = FileOperationUtils.getFileParentFolderRelativePath(rootFolderFullPath, file);
        fileEntity.setRelativePath(relativePath);

        // 获取 file name 和 file extension
        Pair<String, String> fileNameAndExtension = FileOperationUtils.getFileNameAndExtension(file);
        fileEntity.setFileName(fileNameAndExtension.getLeft());
        fileEntity.setFileExtension(fileNameAndExtension.getRight());

        // 获取 uuid4
        String uuid4 = FileOperationUtils.getUUID4(rootFolderId, rootFolderFullPath, file);
        fileEntity.setFileUuid4(uuid4);

        return fileEntity;
    }
}
