package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.entity.FileSyncMappingEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileSyncMappingMapper extends BaseMapper<FileSyncMappingEntity> {
}
