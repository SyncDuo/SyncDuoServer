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
}
