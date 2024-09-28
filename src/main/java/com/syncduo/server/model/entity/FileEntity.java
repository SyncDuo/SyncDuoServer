package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file")
public class FileEntity extends BaseEntity {

    @TableId
    private Long fileId;

    private String fileName;

    private String fileExtension;

    private String fileMd5Checksum;

    private String fileLastModifiedTime;

    private String fileUuid4;

    private Long rootFolderId;

    private Long derivedFileId;

    private String relativePath;

    private Long fileDeleted;

    private Long fileDesync;
}
