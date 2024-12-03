package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.RootFolderMapper;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.service.IRootFolderService;
import com.syncduo.server.util.FileOperationUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

@Service
public class RootFolderService
        extends ServiceImpl<RootFolderMapper, RootFolderEntity>
        implements IRootFolderService {

    public RootFolderEntity getByFolderId(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("获取 Root Folder 失败,folderId 为空");
        }

        RootFolderEntity dbResult = this.getById(folderId);
        return ObjectUtils.isEmpty(dbResult) ? new RootFolderEntity() : dbResult;
    }

    // Pair<sourceFolderEntity, internalFolderEntity>
    public Pair<RootFolderEntity, RootFolderEntity> getFolderPairByPath(String folderFullPath)
            throws SyncDuoException {
        RootFolderEntity sourceFolderEntity = this.getByFolderFullPath(folderFullPath);
        if (ObjectUtils.isEmpty(sourceFolderEntity)) {
            return null;
        }
        String internalFolderFullPath =
                FileOperationUtils.getInternalFolderFullPath(folderFullPath);
        RootFolderEntity internalFolderEntity = this.getByFolderFullPath(internalFolderFullPath);
        if (ObjectUtils.isEmpty(internalFolderEntity)) {
            throw new SyncDuoException("存在 sourceFolder %s, 但是没有 internalFolder".formatted(sourceFolderEntity));
        }
        return new ImmutablePair<>(sourceFolderEntity, internalFolderEntity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RootFolderEntity createContentFolder(
            String sourceFolderFullPath,
            String contentFolderFullPath) throws SyncDuoException {
        RootFolderEntity contentFolderEntity = this.getByFolderFullPath(contentFolderFullPath);
        if (ObjectUtils.isEmpty(contentFolderEntity)) {
            Path contentFolder = FileOperationUtils.createContentFolder(
                    sourceFolderFullPath,
                    contentFolderFullPath
            );
            contentFolderEntity = new RootFolderEntity();
            contentFolderEntity.setRootFolderName(contentFolder.getFileName().toString());
            contentFolderEntity.setRootFolderFullPath(contentFolder.toAbsolutePath().toString());
            contentFolderEntity.setRootFolderType(RootFolderTypeEnum.CONTENT_FOLDER.name());
            boolean saved = this.save(contentFolderEntity);
            if (!saved) {
                FileOperationUtils.deleteFolder(contentFolder);
                throw new SyncDuoException("创建 contentFolder 记录失败,无法保存到数据库");
            }
        }
        return contentFolderEntity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Pair<RootFolderEntity, RootFolderEntity> createSourceFolder(
            String sourceFolderFullPath) throws SyncDuoException  {
        // 幂等, 判断 source folder 是否已创建
        RootFolderEntity sourceFolderEntity = this.getByFolderFullPath(sourceFolderFullPath);
        if (ObjectUtils.isEmpty(sourceFolderEntity)) {
            // 创建 source folder
            Path sourceFolder = FileOperationUtils.isFolderPathValid(sourceFolderFullPath);
            sourceFolderEntity = new RootFolderEntity();
            sourceFolderEntity.setRootFolderName(sourceFolder.getFileName().toString());
            sourceFolderEntity.setRootFolderFullPath(sourceFolder.toAbsolutePath().toString());
            sourceFolderEntity.setRootFolderType(RootFolderTypeEnum.SOURCE_FOLDER.name());
            boolean saved = this.save(sourceFolderEntity);
            if (!saved) {
                throw new SyncDuoException("创建 sourceFolder 记录失败,无法保存到数据库");
            }
        }

        // 文件夹名称 = .<sourceFolder>, 与原文件夹为同一路径
        String internalFolderPath = FileOperationUtils.getInternalFolderFullPath(sourceFolderFullPath);
        RootFolderEntity internalFolderEntity = this.getByFolderFullPath(internalFolderPath);
        if (ObjectUtils.isEmpty(internalFolderEntity)) {
            // 创建 internal folder
            Path internalFolder = FileOperationUtils.createFolder(internalFolderPath);
            internalFolderEntity = new RootFolderEntity();
            internalFolderEntity.setRootFolderName(internalFolder.getFileName().toString());
            internalFolderEntity.setRootFolderFullPath(internalFolder.toAbsolutePath().toString());
            internalFolderEntity.setRootFolderType(RootFolderTypeEnum.INTERNAL_FOLDER.name());
            boolean saved = this.save(internalFolderEntity);
            if (!saved) {
                // 没有保存成功, 则应该删除 internal folder
                FileOperationUtils.deleteFolder(internalFolder);
                throw new SyncDuoException("创建 internalFolder 记录失败,无法保存到数据库");
            }
        }
        return new ImmutablePair<>(sourceFolderEntity, internalFolderEntity);
    }

    public RootFolderEntity getByFolderFullPath(String folderFullPath) throws SyncDuoException {
        if (StringUtils.isEmpty(folderFullPath)) {
            throw new SyncDuoException("参数检查失败, folderFullPath 为空");
        }
        LambdaQueryWrapper<RootFolderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RootFolderEntity::getRootFolderFullPath, folderFullPath);
        queryWrapper.eq(RootFolderEntity::getFolderDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<RootFolderEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return null;
        }
        if (dbResult.size() > 1) {
            throw new SyncDuoException("RootFolder 存在多对一的情况, %s".formatted(dbResult));
        }
        return dbResult.get(0);
    }
}
