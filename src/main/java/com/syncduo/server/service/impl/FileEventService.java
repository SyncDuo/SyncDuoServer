package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileEventMapper;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.service.IFileEventService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class FileEventService
        extends ServiceImpl<FileEventMapper, FileEventEntity>
        implements IFileEventService {

    public List<FileEventEntity> getByFileEventTypeAndFileId(
            FileEventTypeEnum fileEventType,
            Long fileId) throws SyncDuoException {
        if (ObjectUtils.anyNull(fileEventType, fileId)) {
            throw new SyncDuoException("getByFileEventTypeAndFileId failed. " +
                    "fileEventType or fileId is null");
        }
        LambdaQueryWrapper<FileEventEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEventEntity::getFileEventType, fileEventType.toString());
        queryWrapper.eq(FileEventEntity::getFileId, fileId);
        List<FileEventEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return Collections.emptyList();
        }
        dbResult.sort(Comparator.comparing(FileEventEntity::getLastUpdatedTime).reversed());
        return dbResult;
    }

    public List<FileEventEntity> getByFileTypeAndRootFolderId(
            FileEventTypeEnum fileEventType,
            Long rootFolderId) throws SyncDuoException {
        if (ObjectUtils.anyNull(fileEventType, rootFolderId)) {
            throw new SyncDuoException("getByFileEventTypeAndFileId failed. " +
                    "fileEventType or rootFolderId is null");
        }
        LambdaQueryWrapper<FileEventEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEventEntity::getFileEventType, fileEventType.toString());
        queryWrapper.eq(FileEventEntity::getRootFolderId, rootFolderId);
        List<FileEventEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return Collections.emptyList();
        }
        dbResult.sort(Comparator.comparing(FileEventEntity::getLastUpdatedTime).reversed());
        return dbResult;
    }
}
