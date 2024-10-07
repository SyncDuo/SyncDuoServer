package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncSettingMapper extends BaseMapper<SyncSettingEntity> {
}
