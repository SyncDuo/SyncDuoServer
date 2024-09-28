package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file_operation")
public class FileOperationEntity extends BaseEntity {

    @TableId
    private Long fileOperationId;

    private String operationType;

    private Long sourceFileId;

    private Long destRootFolderId;

    private Long syncFlowId;

    private String operationStatus;

    private String errorMessage;

    private Long executeCount;
}
