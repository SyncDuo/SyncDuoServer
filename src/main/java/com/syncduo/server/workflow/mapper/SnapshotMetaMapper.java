package com.syncduo.server.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.workflow.model.db.SnapshotMeta;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SnapshotMetaMapper extends BaseMapper<SnapshotMeta> {
}
