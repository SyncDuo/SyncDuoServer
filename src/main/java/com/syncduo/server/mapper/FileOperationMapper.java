package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.FileOperationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileOperationMapper extends BaseMapper<FileOperationEntity> {
}
