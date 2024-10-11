package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileEventMapper;
import com.syncduo.server.mapper.RootFolderMapper;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.service.IFileEventService;
import com.syncduo.server.service.IRootFolderService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
}
