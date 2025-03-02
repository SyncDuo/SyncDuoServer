package com.syncduo.server.service.bussiness.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.bus.FileAccessValidator;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FolderMapper;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.service.bussiness.IFolderService;
import com.syncduo.server.util.FilesystemUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

@Service
public class FolderService
        extends ServiceImpl<FolderMapper, FolderEntity>
        implements IFolderService {

    private final FileAccessValidator fileAccessValidator;

    @Autowired
    public FolderService(FileAccessValidator fileAccessValidator) {
        this.fileAccessValidator = fileAccessValidator;
    }

    public List<FolderEntity> getAllFolder() throws SyncDuoException {
        LambdaQueryWrapper<FolderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FolderEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.list(queryWrapper);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FolderEntity createFolder(FolderEntity folderEntity) throws SyncDuoException {
        if (ObjectUtils.anyNull(
                folderEntity,
                folderEntity.getFolderFullPath(),
                folderEntity.getFolderName()
        )) {
            throw new SyncDuoException("folderEntity,folderName or folderFullPath is null. %s".formatted(folderEntity));
        }
        // 幂等
        FolderEntity dbResult = this.getByFolderFullPath(folderEntity.getFolderFullPath());
        if (ObjectUtils.isEmpty(dbResult)) {
            folderEntity.setFolderId(null);
            boolean save = this.save(folderEntity);
            if (!save) {
                throw new SyncDuoException("save folder entity to database failed.");
            }
            fileAccessValidator.addWhitelist(folderEntity);
            return folderEntity;
        } else {
            throw new SyncDuoException("folder already exists! folderEntity is %s".formatted(folderEntity));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteFolder(FolderEntity folderEntity) throws SyncDuoException {
        FolderEntity dbResult = this.getById(folderEntity.getFolderId());
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.setRecordDeleted(DeletedEnum.DELETED.getCode());
        boolean updated = this.updateById(dbResult);
        if (!updated) {
            throw new SyncDuoException("update folder entity to database failed.");
        }
        fileAccessValidator.removeWhitelist(folderEntity.getFolderId());
    }

    public FolderEntity getByFolderFullPath(String folderFullPath) throws SyncDuoException {
        if (StringUtils.isEmpty(folderFullPath)) {
            throw new SyncDuoException("参数检查失败, folderFullPath 为空");
        }
        LambdaQueryWrapper<FolderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FolderEntity::getFolderFullPath, folderFullPath);
        queryWrapper.eq(FolderEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<FolderEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return null;
        }
        if (dbResult.size() > 1) {
            throw new SyncDuoException("RootFolder 存在多对一的情况, %s".formatted(dbResult));
        }
        return dbResult.get(0);
    }
}
