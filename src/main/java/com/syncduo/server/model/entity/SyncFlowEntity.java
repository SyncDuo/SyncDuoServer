package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sync_flow")
public class SyncFlowEntity extends BaseEntity {

    @TableId
    private Long syncFlowId;

    private Long sourceFolderId;

    private Long destFolderId;

    private String flowType;

    private String syncStatus;

    private Timestamp lastSyncTime;
}
