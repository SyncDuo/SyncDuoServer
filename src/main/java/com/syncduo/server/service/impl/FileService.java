package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileMapper;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.service.IFileService;
import com.syncduo.server.util.FileOperationUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;

@Service
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

    public FileEntity getByUuid4(String uuid4) throws SyncDuoException {
        if (StringUtils.isEmpty(uuid4)) {
            throw new SyncDuoException("获取文件记录失败, uuid4 为空");
        }
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFileUuid4, uuid4);
        List<FileEntity> dbResult = this.list(queryWrapper);

        return CollectionUtils.isEmpty(dbResult) ? new FileEntity() : dbResult.get(0);
    }

    public FileEntity fillFileEntityForCreate(
            Path file, Long rootFolderId, String folderFullPath) throws SyncDuoException {
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
        String relativePath = FileOperationUtils.getFileParentFolderRelativePath(folderFullPath, file);
        fileEntity.setRelativePath(relativePath);

        // 获取 file name 和 file extension
        Pair<String, String> fileNameAndExtension = FileOperationUtils.getFileNameAndExtension(file);
        fileEntity.setFileName(fileNameAndExtension.getLeft());
        fileEntity.setFileExtension(fileNameAndExtension.getRight());

        // 获取 uuid4
        String uuid4 = FileOperationUtils.getUuid4(file);
        fileEntity.setFileUuid4(uuid4);

        return fileEntity;
    }
}
