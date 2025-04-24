package com.syncduo.server.service.bussiness.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.FileEventMapper;
import com.syncduo.server.mapper.SystemConfigMapper;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.SystemConfigEntity;
import com.syncduo.server.service.bussiness.IFileEventService;
import com.syncduo.server.service.bussiness.ISystemConfigService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class SystemConfigService
        extends ServiceImpl<SystemConfigMapper, SystemConfigEntity>
        implements ISystemConfigService {

        public SystemConfigEntity systemConfigEntity;

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

        public SystemConfigEntity updateSystemConfig(SystemConfigEntity systemConfigEntity) throws SyncDuoException {
            if (ObjectUtils.isEmpty(systemConfigEntity)) {
                throw new SyncDuoException("updateSystemConfig failed. systemConfigEntity is null");
            }
            if (ObjectUtils.isEmpty(systemConfigEntity.getSystemConfigId())) {
                if (ObjectUtils.isNotEmpty(this.systemConfigEntity)) {
                    // 没有主键 ID, 但是有缓存, 说明使用错误
                    throw new SyncDuoException("updateSystemConfig failed. systemConfigId is null");
                }
                // 如果没有主键 ID 且目前没有缓存, 则新建
                boolean save = this.save(systemConfigEntity);
                if (!save) {
                    throw new SyncDuoException("updateSystemConfig failed. can't write to database.");
                }
            }
            // 如果有主键 ID, 则覆盖或新增缓存
            int count = this.baseMapper.updateById(systemConfigEntity);
            if (count != 1) {
                throw new SyncDuoException("updateSystemConfig failed. can't write to database.");
            }
            this.systemConfigEntity = systemConfigEntity;
            return this.systemConfigEntity;
        }
}
