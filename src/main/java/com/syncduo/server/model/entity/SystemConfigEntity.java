package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("system_config")
public class SystemConfigEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Integer systemConfigId; // int id, 用于传递给前端

    private String backupStoragePath;
}
