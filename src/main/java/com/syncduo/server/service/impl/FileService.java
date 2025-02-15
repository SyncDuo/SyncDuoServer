package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncSettingEnum;
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
        // 更新  md5 checksum, last_modified_time
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
            throw new SyncDuoException("rootFolderId %s 或 file %s 为空".formatted(rootFolderId, file));
        }
        String uuid4 = FileOperationUtils.getUUID4(rootFolderId, rootFolderFullPath, file);
        return this.getByUUID4(uuid4);
    }

    public FileEntity getInternalFileEntityFromSourceEntity(Long destFolderId, FileEntity sourceFileEntity)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(destFolderId, sourceFileEntity)) {
            throw new SyncDuoException("获取file失败, destFolderId 或 sourceFileEntity 为空");
        }
        String fileName = sourceFileEntity.getFileName();
        if (StringUtils.isNotBlank(sourceFileEntity.getFileExtension())) {
            fileName = fileName + "." + sourceFileEntity.getFileExtension();
        }
        String uuid4 = FileOperationUtils.getUUID4(
                destFolderId,
                sourceFileEntity.getRelativePath(),
                fileName
        );
        return this.getByUUID4(uuid4);
    }

    public FileEntity getContentFileEntityFromInternalEntity(
            Long destFolderId,
            FileEntity internalFileEntity,
            SyncSettingEnum syncSetting)
            throws SyncDuoException {
        String uuid4 = getContentFileUUID4FromInternalFileEntity(destFolderId, internalFileEntity, syncSetting);
        FileEntity contentFileEntity = this.getByUUID4(uuid4);
        return ObjectUtils.isEmpty(contentFileEntity) ? null : contentFileEntity;
    }

    public FileEntity getContentFileEntityFromInternalEntityIgnoreDeleted(
            Long destFolderId,
            FileEntity sourceFileEntity,
            SyncSettingEnum syncSetting)
            throws SyncDuoException {
        String uuid4 = getContentFileUUID4FromInternalFileEntity(destFolderId, sourceFileEntity, syncSetting);
        List<FileEntity> contentFileEntityList = this.getByUuid4IgnoredDelete(uuid4);
        if (CollectionUtils.isEmpty(contentFileEntityList)) {
            return null;
        }
        contentFileEntityList.sort(
                (o1, o2) -> o2.getLastUpdatedTime().compareTo(o1.getLastUpdatedTime()));
        return contentFileEntityList.get(0);
    }

    private String getContentFileUUID4FromInternalFileEntity(
            Long destFolderId,
            FileEntity internalFileEntity,
            SyncSettingEnum syncSetting) throws SyncDuoException {
        if (ObjectUtils.anyNull(destFolderId, internalFileEntity, syncSetting)) {
            throw new SyncDuoException("获取 file entity 失败, destFolderId, internalFileEntity 或 syncSetting 为空");
        }
        String fileName = internalFileEntity.getFileName();
        if (StringUtils.isNotBlank(internalFileEntity.getFileExtension())) {
            fileName = fileName + "." + internalFileEntity.getFileExtension();
        }
        String uuid4;
        if (syncSetting.equals(SyncSettingEnum.MIRROR)) {
            uuid4 = FileOperationUtils.getUUID4(
                    destFolderId,
                    internalFileEntity.getRelativePath(),
                    fileName
            );
        } else {
            uuid4 = FileOperationUtils.getUUID4(
                    destFolderId,
                    FileOperationUtils.getPathSeparator(),
                    fileName
            );
        }
        return uuid4;
    }

    public Path getFileFromFileEntity(String rootFolderFullPath, FileEntity fileEntity) throws SyncDuoException {
        return FileOperationUtils.isFilePathValid(concatPathStringFromFolderAndFile(rootFolderFullPath, fileEntity));
    }

    public String concatPathStringFromFolderAndFile(String rootFolderFullPath, FileEntity fileEntity)
            throws SyncDuoException {
        if (StringUtils.isBlank(rootFolderFullPath)) {
            throw new SyncDuoException("rootFolderFullPath 为空");
        }
        if (ObjectUtils.isEmpty(fileEntity)) {
            throw new SyncDuoException("fileEntity 为空");
        }
        String filePath = rootFolderFullPath +
                fileEntity.getRelativePath() +
                FileOperationUtils.getPathSeparator() +
                fileEntity.getFileName();
        if (StringUtils.isNotBlank(fileEntity.getFileExtension())) {
            filePath = filePath + "." + fileEntity.getFileExtension();
        }
        return filePath;
    }

    public String concatContentFilePathFlattenFolder(
            String contentFolderPath,
            FileEntity internalFileEntity)
            throws SyncDuoException {
        if (StringUtils.isBlank(contentFolderPath)) {
            throw new SyncDuoException("contentFolderPath 为空");
        }
        if (ObjectUtils.isEmpty(internalFileEntity)) {
            throw new SyncDuoException("internalFileEntity 为空");
        }
        String filePath = contentFolderPath +
                FileOperationUtils.getPathSeparator() +
                internalFileEntity.getFileUuid4();
        if (StringUtils.isNotBlank(internalFileEntity.getFileExtension())) {
            filePath = filePath + "." + internalFileEntity.getFileExtension();
        }
        return filePath;
    }

    public FileEntity getByUUID4(String uuid4) throws SyncDuoException {
        if (StringUtils.isEmpty(uuid4)) {
            throw new SyncDuoException("getByUUID4 failed. uuid4 is null");
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFileUuid4, uuid4);
        queryWrapper.eq(FileEntity::getFileDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<FileEntity> dbResult = this.list(queryWrapper);

        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    // 三种形态
    // FILE_SYNC, 则 FILE NOT DELETE
    // FILE_DESYNC, FILE DELETE
    // FILE_DESYNC, FILE NOT DELETE
    private List<FileEntity> getByUuid4IgnoredDelete(String uuid4) throws SyncDuoException {
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
        String relativePath = FileOperationUtils.getRelativePath(rootFolderFullPath, file);
        fileEntity.setRelativePath(relativePath);

        // 获取 file name 和 file extension
        Pair<String, String> fileNameAndExtension = FileOperationUtils.getFileNameAndExtension(file);
        fileEntity.setFileName(fileNameAndExtension.getLeft());
        String fileFullName = fileNameAndExtension.getLeft();
        fileEntity.setFileExtension(fileNameAndExtension.getRight());
        if (StringUtils.isNotBlank(fileNameAndExtension.getRight())) {
            fileFullName = fileFullName + "." + fileNameAndExtension.getRight();
        }

        // 获取 uuid4
        String uuid4 = FileOperationUtils.getUUID4(rootFolderId, relativePath, fileFullName);
        fileEntity.setFileUuid4(uuid4);

        return fileEntity;
    }
}
