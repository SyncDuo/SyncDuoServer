package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

import java.nio.file.Path;
import java.util.List;

@Service
public class RootFolderService
        extends ServiceImpl<RootFolderMapper, RootFolderEntity>
        implements IRootFolderService {
    private final String internalFolderPath;

    public RootFolderService() {
//        ApplicationHome home = new ApplicationHome(this.getClass());
//        File jarFile = home.getSource();
//        String jarFolderFullPath = Paths.get(jarFile.getParent()).toAbsolutePath().normalize().toString();
//        this.internalFolderPath = jarFolderFullPath + FileOperationUtils.getSeparator() + ".internalFolder";
        this.internalFolderPath = "/home/nopepsi-dev/IdeaProject/SyncDuoServer/src/test/folder/internalFolder";
    }

    public RootFolderEntity getByFolderId(Long folderId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderId)) {
            throw new SyncDuoException("获取 Root Folder 失败,folderId 为空");
        }

        RootFolderEntity dbResult = this.getById(folderId);
        return ObjectUtils.isEmpty(dbResult) ? new RootFolderEntity() : dbResult;
    }

    public RootFolderEntity createContentFolder(String contentFolderFullPath) throws SyncDuoException {
        RootFolderEntity contentFolderEntity = this.getByFolderFullPath(contentFolderFullPath);
        if (ObjectUtils.isEmpty(contentFolderEntity)) {
            Path contentFolder = FileOperationUtils.isFolderPathValid(contentFolderFullPath);
            contentFolderEntity = new RootFolderEntity();
            contentFolderEntity.setRootFolderName(contentFolder.getFileName().toString());
            contentFolderEntity.setRootFolderFullPath(contentFolder.toAbsolutePath().toString());
            contentFolderEntity.setRootFolderType(RootFolderTypeEnum.CONTENT_FOLDER.name());
            boolean saved = this.save(contentFolderEntity);
            if (!saved) {
                throw new SyncDuoException("创建 contentFolder 记录失败,无法保存到数据库");
            }
        }
        return contentFolderEntity;
    }

    public Pair<RootFolderEntity, RootFolderEntity> createSourceFolder(
            String sourceFolderFullPath) throws SyncDuoException  {
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

        String internalFolderPath = this.internalFolderPath +
                FileOperationUtils.getSeparator() +
                FileOperationUtils.getFolderNameFromFullPath(sourceFolderFullPath);
        RootFolderEntity internalFolderEntity = this.getByFolderFullPath(internalFolderPath);
        if (ObjectUtils.isEmpty(internalFolderEntity)) {
            // 创建 internal folder
            Path internalFolder = FileOperationUtils.isFolderPathValid(internalFolderPath);
            internalFolderEntity = new RootFolderEntity();
            internalFolderEntity.setRootFolderName(internalFolder.getFileName().toString());
            internalFolderEntity.setRootFolderFullPath(internalFolder.toAbsolutePath().toString());
            internalFolderEntity.setRootFolderType(RootFolderTypeEnum.INTERNAL_FOLDER.name());
            boolean saved = this.save(internalFolderEntity);
            if (!saved) {
                throw new SyncDuoException("创建 internalFolder 记录失败,无法保存到数据库");
            }
        }
        return new ImmutablePair<>(sourceFolderEntity, internalFolderEntity);
    }

    private RootFolderEntity getByFolderFullPath(String folderFullPath) throws SyncDuoException {
        if (StringUtils.isEmpty(folderFullPath)) {
            throw new SyncDuoException("参数检查失败, folderFullPath 为空");
        }

        LambdaQueryWrapper<RootFolderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RootFolderEntity::getRootFolderFullPath, folderFullPath);
        List<RootFolderEntity> dbResult = this.list(queryWrapper);

        if (CollectionUtils.isEmpty(dbResult)) {
            return new RootFolderEntity();
        }
        if (dbResult.size() > 1) {
            throw new SyncDuoException("RootFolder 存在多对一的情况, %s".formatted(dbResult));
        }
        return dbResult.get(0);
    }
}
