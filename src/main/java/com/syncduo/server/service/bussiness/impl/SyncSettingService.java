package com.syncduo.server.service.bussiness.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.SyncModeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncSettingMapper;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.service.bussiness.ISyncSettingService;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SyncSettingService
        extends ServiceImpl<SyncSettingMapper, SyncSettingEntity>
        implements ISyncSettingService {

    public SyncSettingEntity createSyncSetting(Long syncFlowId, List<String> filters, SyncModeEnum syncModeEnum)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId, syncModeEnum)) {
            throw new SyncDuoException("创建 syncSetting 失败, syncFlowId 或 syncSettingEnum 为空");
        }
        SyncSettingEntity syncSettingEntity = new SyncSettingEntity();
        syncSettingEntity.setSyncFlowId(syncFlowId);
        syncSettingEntity.setSyncMode(syncModeEnum.getCode());
        if (CollectionUtils.isEmpty(filters)) {
            filters = new ArrayList<>();
        }
        String filterCriteria = JsonUtil.serializeListToString(filters);
        syncSettingEntity.setFilterCriteria(filterCriteria);
        boolean save = this.save(syncSettingEntity);
        if (!save) {
            throw new SyncDuoException("保存 syncSetting 失败. %s".formatted(syncSettingEntity));
        }
        return syncSettingEntity;
    }

    public SyncSettingEntity getBySyncFlowId(Long syncFlowId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowId)) {
            throw new SyncDuoException("syncFlowId 为空");
        }
        LambdaQueryWrapper<SyncSettingEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncSettingEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(SyncSettingEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncSettingEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public boolean isMirrored(Long syncFlowId) throws SyncDuoException {
        SyncSettingEntity syncSettingEntity = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            throw new SyncDuoException("没有找到同步设置, syncFlowId %s".formatted(syncFlowId));
        }
        SyncModeEnum syncModeEnum = SyncModeEnum.getByCode(syncSettingEntity.getSyncMode());
        if (ObjectUtils.isEmpty(syncModeEnum)) {
            throw new SyncDuoException("无法读取 flatten folder 设置." +
                    "syncSettingEntity is %s".formatted(syncSettingEntity));
        }
        return syncModeEnum.equals(SyncModeEnum.MIRROR);
    }

    public boolean isFilter(Long syncFlowId, Path file) throws SyncDuoException {
        SyncSettingEntity syncSettingEntity = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            throw new SyncDuoException("没有找到同步设置, syncFlowId %s".formatted(syncFlowId));
        }
        String fileFullName = file.getFileName().toString();
        String filterCriteria = syncSettingEntity.getFilterCriteria();
        List<String> filters = JsonUtil.deserializeStringToList(filterCriteria);
        for (String filter : filters) {
            if (fileFullName.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFilter(SyncSettingEntity syncSettingEntity, String fileExtension) throws SyncDuoException {
        if (StringUtils.isBlank(fileExtension)) {
            return false;
        }
        String filterCriteria = syncSettingEntity.getFilterCriteria();
        List<String> filters = JsonUtil.deserializeStringToList(filterCriteria);
        for (String filter : filters) {
            if (fileExtension.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getFilterCriteria(SyncSettingEntity syncSettingEntity) throws SyncDuoException {
        return JsonUtil.deserializeStringToList(syncSettingEntity.getFilterCriteria());
    }

    public void deleteSyncSetting(Long syncFLowId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFLowId)) {
            return;
        }
        SyncSettingEntity syncSettingEntity = this.getBySyncFlowId(syncFLowId);
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            return;
        }
        syncSettingEntity.setRecordDeleted(DeletedEnum.DELETED.getCode());
        boolean update = this.updateById(syncSettingEntity);
        if (!update) {
            throw new SyncDuoException("deleteSyncSetting failed. " +
                    "can't write to database.");
        }
    }
}
