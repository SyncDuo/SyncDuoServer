package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<FileEntity> {
}
