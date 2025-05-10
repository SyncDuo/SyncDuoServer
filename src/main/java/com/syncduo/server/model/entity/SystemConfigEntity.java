package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("system_config")
public class SystemConfigEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long systemConfigId;

    private String syncStoragePath;

    private String backupStoragePath;

    private Integer handlerMinThreads;

    private Integer handlerMaxThreads;
}
