package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SystemConfigMapper;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.service.db.ISystemConfigService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemConfigService
        extends ServiceImpl<SystemConfigMapper, SystemConfigEntity> implements ISystemConfigService {

    private SystemConfigEntity systemConfigEntity;

    public void clearCache() {
            this.systemConfigEntity = null;
    }

    public SystemConfigEntity getSystemConfig() {
        if (ObjectUtils.isNotEmpty(systemConfigEntity)) {
            return this.systemConfigEntity;
        }
        LambdaQueryWrapper<SystemConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SystemConfigEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SystemConfigEntity> dbResult = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            this.systemConfigEntity = null;
        } else {
            this.systemConfigEntity = dbResult.get(0);
        }
        return this.systemConfigEntity;
    }

    public void createSystemConfig(SystemConfigEntity systemConfigEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(systemConfigEntity)) {
            throw new SyncDuoException("createSystemConfig failed. systemConfigEntity is null");
        }
        if (ObjectUtils.isNotEmpty(systemConfigEntity.getSystemConfigId())) {
            throw new SyncDuoException("createSystemConfig failed. Should use update method.");
        }
        boolean save = this.save(systemConfigEntity);
        if (!save) {
            throw new SyncDuoException("createSystemConfig failed. can't write to database");
        }
        this.systemConfigEntity = systemConfigEntity;
    }

    // 更新要求先获取
    public SystemConfigEntity updateSystemConfig(SystemConfigEntity systemConfigEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(systemConfigEntity)) {
            throw new SyncDuoException("updateSystemConfig failed. systemConfigEntity is null");
        }
        if (ObjectUtils.isEmpty(systemConfigEntity.getSystemConfigId())) {
            throw new SyncDuoException("updateSystemConfig failed. systemConfigId is null." +
                    "are you creating system config?");
        }
        // 如果有主键 ID, 则覆盖或新增缓存
        boolean updated = this.updateById(systemConfigEntity);
        if (!updated) {
            throw new SyncDuoException("updateSystemConfig failed. can't write to database.");
        }
        // 更新缓存的 systemConfigEntity
        this.systemConfigEntity = systemConfigEntity;
        return this.systemConfigEntity;
    }
}
