package com.syncduo.server.service.bussiness.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileMapper;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.service.bussiness.IFileService;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class FileService extends ServiceImpl<FileMapper, FileEntity> implements IFileService {

    public List<FileEntity> getByFileIds(Collection<? extends Serializable> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(FileEntity::getFileId, idList);
        queryWrapper.eq(FileEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.list(queryWrapper);
    }

    public boolean isFileEntityDiffFromFile(FileEntity fileEntity, Path file) throws SyncDuoException {
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FilesystemUtil.getFileCrTimeAndMTime(file);
        String md5Checksum = FilesystemUtil.getMD5Checksum(file);
        if (!fileEntity.getFileMd5Checksum().equals(md5Checksum)) {
            return true;
        }
        return !fileEntity.getFileLastModifiedTime().equals(fileCrTimeAndMTime.getRight());
    }

    public boolean isSourceFileEntityDiffFromDest(
            FileEntity sourceFileEntity,
            FileEntity destFileEntity) {
        return !sourceFileEntity.getFileMd5Checksum().equals(destFileEntity.getFileMd5Checksum());
    }

    public List<FileEntity> getAllFileByFolderId(Long folderId) {
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFolderId, folderId);
        queryWrapper.eq(FileEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.list(queryWrapper);
    }

    public FileEntity createFileRecord(
            Long folderId,
            String folderFullPath,
            Path file
    ) throws SyncDuoException {
        if (ObjectUtils.anyNull(folderId, folderFullPath, file)) {
            throw new SyncDuoException("createFileRecord failed." +
                    "folderId, folderFullPath or file is null");
        }
        FileEntity fileEntity = this.fillFileEntityForCreate(
                file,
                folderId,
                folderFullPath
        );
        boolean save = this.save(fileEntity);
        if (!save) {
            throw new SyncDuoException("createFileRecord failed. can't save to database");
        }
        return fileEntity;
    }

    public void updateFileEntityByFile(FileEntity fileEntity, Path file) throws SyncDuoException {
        if (ObjectUtils.anyNull(fileEntity, file)) {
            throw new SyncDuoException("获取file失败, fileEntity 或 file 为空");
        }

        // 更新  md5 checksum, last_modified_time
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FilesystemUtil.getFileCrTimeAndMTime(file);
        fileEntity.setLastUpdatedTime(fileCrTimeAndMTime.getRight());
        String md5Checksum = FilesystemUtil.getMD5Checksum(file);
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
            fileEntity.setRecordDeleted(DeletedEnum.DELETED.getCode());
        }
        boolean updated = this.updateBatchById(fileEntityList);
        if (!updated) {
            throw new SyncDuoException("删除失败");
        }
    }

    public FileEntity getFileEntityFromFile(
            Long folderId,
            String folderFullPath,
            Path file)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(folderId, file)) {
            throw new SyncDuoException("folderId %s 或 file %s 为空".formatted(folderId, file));
        }
        String uniqueHash = FilesystemUtil.getUniqueHash(folderId, folderFullPath, file);
        return this.getByUniqueHash(uniqueHash);
    }

    public Path getFileFromFileEntity(String folderFullPath, FileEntity fileEntity) throws SyncDuoException {
        return FilesystemUtil.isFilePathValid(concatPathStringFromFolderAndFile(folderFullPath, fileEntity));
    }

    public String concatPathStringFromFolderAndFile(String folderFullPath, FileEntity fileEntity)
            throws SyncDuoException {
        if (StringUtils.isBlank(folderFullPath)) {
            throw new SyncDuoException("folderFullPath 为空");
        }
        if (ObjectUtils.isEmpty(fileEntity)) {
            throw new SyncDuoException("fileEntity 为空");
        }
        String filePath = folderFullPath +
                fileEntity.getRelativePath() +
                FilesystemUtil.getPathSeparator() +
                fileEntity.getFileName();
        if (StringUtils.isNotBlank(fileEntity.getFileExtension())) {
            filePath = filePath + "." + fileEntity.getFileExtension();
        }
        return filePath;
    }

    public FileEntity getByUniqueHash(String uniqueHash) throws SyncDuoException {
        if (StringUtils.isEmpty(uniqueHash)) {
            throw new SyncDuoException("getByUniqueHash failed. uniqueHash is null");
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFileUniqueHash, uniqueHash);
        queryWrapper.eq(FileEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<FileEntity> dbResult = this.list(queryWrapper);

        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public FileEntity fillFileEntityForCreate(
            Path file, Long folderId, String folderFullPath) throws SyncDuoException {
        FileEntity fileEntity = new FileEntity();

        // 获取 created_time and last_modified_time
        Pair<Timestamp, Timestamp> fileCrTimeAndMTime = FilesystemUtil.getFileCrTimeAndMTime(file);
        fileEntity.setFileCreatedTime(fileCrTimeAndMTime.getLeft());
        fileEntity.setFileLastModifiedTime(fileCrTimeAndMTime.getRight());
        // 获取 md5 checksum
        String md5Checksum = FilesystemUtil.getMD5Checksum(file);
        fileEntity.setFileMd5Checksum(md5Checksum);
        // 设置 folder id , 根据 folder full path 计算 relative path
        fileEntity.setFolderId(folderId);
        String relativePath = FilesystemUtil.getRelativePath(folderFullPath, file);
        fileEntity.setRelativePath(relativePath);
        // 获取 file name 和 file extension
        Pair<String, String> fileNameAndExtension = FilesystemUtil.getFileNameAndExtension(file);
        fileEntity.setFileName(fileNameAndExtension.getLeft());
        fileEntity.setFileExtension(fileNameAndExtension.getRight());
        // 获取 fileUniqueHash
        String fileUniqueHash = FilesystemUtil.getUniqueHash(folderId, folderFullPath, file);
        fileEntity.setFileUniqueHash(fileUniqueHash);

        return fileEntity;
    }
}
