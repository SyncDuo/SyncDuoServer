package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileEventMapper extends BaseMapper<FileEventEntity> {
}
