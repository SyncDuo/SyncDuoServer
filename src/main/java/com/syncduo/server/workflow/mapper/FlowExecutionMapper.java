package com.syncduo.server.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.workflow.model.db.FlowExecutionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowExecutionMapper extends BaseMapper<FlowExecutionEntity> {
}
