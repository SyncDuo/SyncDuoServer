package com.syncduo.server.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncSettingMapper;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.service.db.ISyncSettingService;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SyncSettingService
        extends ServiceImpl<SyncSettingMapper, SyncSettingEntity>
        implements ISyncSettingService {

    public void createSyncSetting(Long syncFlowId, String filterCriteria)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId)) {
            throw new SyncDuoException("创建 syncSetting 失败, syncFlowId 为空");
        }
        SyncSettingEntity dbResult = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            return;
        }
        SyncSettingEntity syncSettingEntity = new SyncSettingEntity();
        syncSettingEntity.setSyncFlowId(syncFlowId);
        syncSettingEntity.setFilterCriteria(filterCriteria);
        boolean save = this.save(syncSettingEntity);
        if (!save) {
            throw new SyncDuoException("createSyncSetting failed. can't save to database.");
        }
    }

    public SyncSettingEntity createSyncSetting(Long syncFlowId, List<String> filters)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId)) {
            throw new SyncDuoException("创建 syncSetting 失败, syncFlowId 为空");
        }
        SyncSettingEntity syncSettingEntity = new SyncSettingEntity();
        syncSettingEntity.setSyncFlowId(syncFlowId);
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

    public SyncSettingEntity getBySyncFlowId(long syncFlowId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowId)) {
            throw new SyncDuoException("syncFlowId 为空");
        }
        LambdaQueryWrapper<SyncSettingEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncSettingEntity::getSyncFlowId, syncFlowId);
        queryWrapper.eq(SyncSettingEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<SyncSettingEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public List<String> getFilterCriteria(long syncFlowId) throws SyncDuoException {
        SyncSettingEntity syncSettingEntity = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            return null;
        }
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
