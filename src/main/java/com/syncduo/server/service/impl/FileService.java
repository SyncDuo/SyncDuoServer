package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileMapper;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.service.IFileService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

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
}
