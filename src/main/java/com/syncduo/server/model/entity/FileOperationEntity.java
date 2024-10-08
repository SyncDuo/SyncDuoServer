package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file_operation")
public class FileOperationEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long fileOperationId;

    private String operationType;

    private Long sourceFileId;

    private Long destRootFolderId;

    private Long syncFlowId;

    private String operationStatus;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private Integer executeCount;
}
