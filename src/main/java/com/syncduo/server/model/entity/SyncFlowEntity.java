package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sync_flow")
public class SyncFlowEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long syncFlowId;

    private Long sourceFolderId;

    private Long destFolderId;

    private String flowType;

    @TableField(fill = FieldFill.INSERT)
    private String syncStatus;

    private Timestamp lastSyncTime;
}
