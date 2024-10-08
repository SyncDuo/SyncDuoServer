package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sync_setting")
public class SyncSettingEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long syncSettingId;

    private Long syncFlowId;

    @TableField(fill = FieldFill.INSERT)
    private String filterCriteria;

    @TableField(fill = FieldFill.INSERT)
    private Integer flattenFolder;
}
