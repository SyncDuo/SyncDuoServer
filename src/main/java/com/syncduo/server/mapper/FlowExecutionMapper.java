package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.db.FlowExecutionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowExecutionMapper extends BaseMapper<FlowExecutionEntity> {
}
