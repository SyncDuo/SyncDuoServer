package com.syncduo.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.model.entity.BackupJobEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BackupJobMapper extends BaseMapper<BackupJobEntity> {
}
