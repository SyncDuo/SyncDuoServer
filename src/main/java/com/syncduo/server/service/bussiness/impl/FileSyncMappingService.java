package com.syncduo.server.service.bussiness.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileSyncMappingMapper;
import com.syncduo.server.model.entity.FileSyncMappingEntity;
import com.syncduo.server.service.bussiness.IFileSyncMappingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FileSyncMappingService
        extends ServiceImpl<FileSyncMappingMapper, FileSyncMappingEntity>
        implements IFileSyncMappingService {

    public void desyncBySourceFileId(Long fileId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileId)) {
            throw new SyncDuoException("desyncByFileId failed. fileId is null");
        }
        LambdaUpdateWrapper<FileSyncMappingEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FileSyncMappingEntity::getSourceFileId, fileId);
        updateWrapper.set(FileSyncMappingEntity::getFileDesync, FileDesyncEnum.FILE_DESYNC.getCode());
        boolean update = this.update(updateWrapper);
        if (!update) {
            throw new SyncDuoException("desyncByFileId failed. can't not update database");
        }
    }

    public void desyncByDestFileId(Long fileId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileId)) {
            throw new SyncDuoException("desyncByFileId failed. fileId is null");
        }
        LambdaUpdateWrapper<FileSyncMappingEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FileSyncMappingEntity::getDestFileId, fileId);
        updateWrapper.set(FileSyncMappingEntity::getFileDesync, FileDesyncEnum.FILE_DESYNC.getCode());
        boolean update = this.update(updateWrapper);
        if (!update) {
            throw new SyncDuoException("desyncByFileId failed. can't not update database");
        }
    }

    public FileSyncMappingEntity createRecord(
            Long syncFlowId,
            Long sourceFileId,
            Long destFileId
    ) throws SyncDuoException {
        // 幂等
        FileSyncMappingEntity dbResult = this.getBySyncFlowIdAndFileId(syncFlowId, sourceFileId);
        if (ObjectUtils.isEmpty(dbResult)) {
            FileSyncMappingEntity fileSyncMappingEntity = new FileSyncMappingEntity();
            fileSyncMappingEntity.setSyncFlowId(syncFlowId);
            fileSyncMappingEntity.setSourceFileId(sourceFileId);
            fileSyncMappingEntity.setDestFileId(destFileId);
            fileSyncMappingEntity.setFileDesync(FileDesyncEnum.FILE_SYNC.getCode());
            boolean save = this.save(fileSyncMappingEntity);
            if (!save) {
                throw new SyncDuoException("createRecord failed. can't save file to database.");
            }
            return dbResult;
        } else {
            throw new SyncDuoException("createRecord failed. fileSyncMapping already exist!" +
                    "syncFlowId is %s, sourceFileId is %s, destFileId is %s."
                            .formatted(syncFlowId, sourceFileId, destFileId));
        }
    }

    public void deleteRecord(FileSyncMappingEntity fileSyncMappingEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileSyncMappingEntity.getFileSyncMappingId())) {
            return;
        }
        fileSyncMappingEntity.setRecordDeleted(DeletedEnum.DELETED.getCode());
        boolean save = this.updateById(fileSyncMappingEntity);
        if (!save) {
            throw new SyncDuoException("deleteRecord failed. can't write to database!");
        }
    }

    public FileSyncMappingEntity getBySyncFlowIdAndFileId(Long syncFlowId, Long fileId) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId, fileId)) {
            throw new SyncDuoException("isFileIdExist failed. syncFlowId or fileId is null");
        }
        LambdaQueryWrapper<FileSyncMappingEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileSyncMappingEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(FileSyncMappingEntity::getSourceFileId, fileId);
        queryWrapper.eq(FileSyncMappingEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<FileSyncMappingEntity> dbResult = this.list(queryWrapper);

        return dbResult.isEmpty() ? null : dbResult.get(0);
    }
}
