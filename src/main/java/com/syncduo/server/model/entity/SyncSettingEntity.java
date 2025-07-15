package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sync_setting")
public class SyncSettingEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long syncSettingId;

    private Long syncFlowId;

    private String filterCriteria;
}
