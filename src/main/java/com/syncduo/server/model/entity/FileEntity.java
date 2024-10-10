package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file")
public class FileEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    private String fileName;

    private String fileExtension;

    private String fileMd5Checksum;

    private Timestamp fileCreatedTime;

    private Timestamp fileLastModifiedTime;

    private String fileUuid4;

    private Long rootFolderId;

    private Long derivedFileId;

    private String relativePath;

    @TableField(fill = FieldFill.INSERT)
    private Integer fileDeleted;

    @TableField(fill = FieldFill.INSERT)
    private Integer fileDesync;
}
