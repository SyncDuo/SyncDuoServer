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

        public SystemConfigEntity getSystemConfig() {
            if (ObjectUtils.isNotEmpty(systemConfigEntity)) {
                return this.systemConfigEntity;
            }
            LambdaQueryWrapper<SystemConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemConfigEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
            List<SystemConfigEntity> dbResult = this.list(queryWrapper);
            if (CollectionUtils.isEmpty(dbResult)) {
                return null;
            }
            this.systemConfigEntity = dbResult.get(0);
            return this.systemConfigEntity;
        }

        public SystemConfigEntity updateConfigEntity(SystemConfigEntity systemConfigEntity) throws SyncDuoException {
            if (ObjectUtils.isEmpty(systemConfigEntity)) {
                throw new SyncDuoException("updateConfigEntity failed. systemConfigEntity is null.");
            }
            if (ObjectUtils.isEmpty(this.systemConfigEntity)) {
                boolean saved = this.save(systemConfigEntity);
                if (!saved) {
                    throw new SyncDuoException("updateConfigEntity failed. " +
                            "can't write to database.");
                }
                this.systemConfigEntity = systemConfigEntity;
                return this.systemConfigEntity;
            }
            Long systemConfigId = systemConfigEntity.getSystemConfigId();
            Class<? extends SystemConfigEntity> clazz = systemConfigEntity.getClass();
            for (Field declaredField : clazz.getDeclaredFields()) {
                declaredField.setAccessible(true);
                try {
                    Object sourceValue = declaredField.get(systemConfigEntity);
                    if (ObjectUtils.isNotEmpty(sourceValue)) {
                        declaredField.set(this.systemConfigEntity, sourceValue);
                    }
                } catch (IllegalAccessException e) {
                    throw new SyncDuoException("updateConfigEntity failed. " +
                            "field %s not accessible.".formatted(declaredField));
                }
            }
            this.systemConfigEntity.setSystemConfigId(systemConfigId);
            boolean update = this.updateById(this.systemConfigEntity);
            if (!update) {
                throw new SyncDuoException("updateConfigEntity failed. " +
                        "can't write to database.");
            }
            return this.systemConfigEntity;
        }
}
