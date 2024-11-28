package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncduo.server.enums.SyncSettingEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mapper.SyncFlowMapper;
import com.syncduo.server.mapper.SyncSettingMapper;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.service.ISyncFlowService;
import com.syncduo.server.service.ISyncSettingService;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> LIST_STRING_TYPE_REFERENCE = new TypeReference<>(){};

    public SyncSettingEntity createSyncSetting(Long syncFlowId, List<String> filters, SyncSettingEnum syncSettingEnum)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(syncFlowId, syncSettingEnum)) {
            throw new SyncDuoException("创建 syncSetting 失败, syncFlowId 或 flattenFolder 为空");
        }
        SyncSettingEntity syncSettingEntity = new SyncSettingEntity();
        syncSettingEntity.setSyncFlowId(syncFlowId);
        syncSettingEntity.setFlattenFolder(syncSettingEnum.getCode());
        if (CollectionUtils.isEmpty(filters)) {
            filters = new ArrayList<>();
        }
        try {
            String filterCriteria = OBJECT_MAPPER.writeValueAsString(filters);
            syncSettingEntity.setFilterCriteria(filterCriteria);
        } catch (JsonProcessingException e) {
            throw new SyncDuoException("无法将 filters 序列化为字符串. %s".formatted(filters), e);
        }
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
        List<SyncSettingEntity> dbResult = this.list(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    public boolean isFlattenFolder(Long syncFlowId) throws SyncDuoException {
        SyncSettingEntity syncSettingEntity = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            throw new SyncDuoException("没有找到同步设置, syncFlowId %s".formatted(syncFlowId));
        }
        Integer flattenFolder = syncSettingEntity.getFlattenFolder();
        if (SyncSettingEnum.FLATTEN_FOLDER.getCode() == flattenFolder) {
            return true;
        } else if (SyncSettingEnum.MIRROR.getCode() == flattenFolder) {
            return false;
        } else {
            log.error("无法读取 flatten folder 设置, syncSetting {}", syncSettingEntity);
            return false;
        }
    }

    public boolean isFilter(Long syncFlowId, Path file) throws SyncDuoException {
        SyncSettingEntity syncSettingEntity = this.getBySyncFlowId(syncFlowId);
        if (ObjectUtils.isEmpty(syncSettingEntity)) {
            throw new SyncDuoException("没有找到同步设置, syncFlowId %s".formatted(syncFlowId));
        }
        String fileFullName = file.getFileName().toString();
        String filterCriteria = syncSettingEntity.getFilterCriteria();
        try {
            List<String> filters = OBJECT_MAPPER.readValue(filterCriteria, LIST_STRING_TYPE_REFERENCE);
            for (String filter : filters) {
                if (fileFullName.contains(filter)) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException e) {
            log.error("无法读取过滤条件, syncSetting {}", syncSettingEntity);
            return false;
        }
    }
}
