package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file_sync_mapping")
public class FileSyncMappingEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long fileSyncMappingId;

    private Long syncFlowId;

    private Long sourceFileId;

    private Long destFileId;

    private Integer fileDesync;
}
