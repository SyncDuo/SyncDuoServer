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

    private String syncFlowType;

    @TableField(fill = FieldFill.INSERT)
    private String syncStatus;

    private String syncFlowName;

    private Timestamp lastSyncTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer syncFlowDeleted;
}
