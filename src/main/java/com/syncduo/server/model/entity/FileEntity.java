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

    // extension without dot(.)
    // or "" represent no extension
    private String fileExtension;

    private String fileMd5Checksum;

    private Timestamp fileCreatedTime;

    private Timestamp fileLastModifiedTime;

    // hash from <folderId><fileName><fileExtension>
    private String fileUniqueHash;

    private Long folderId;

    // always starts with "/"
    // if no sub folder, then only contains "/"
    // else ends with "folder name"
    // eg: <parent folder><relativePath> which <relativePath> contains prefix "/"
    private String relativePath;
}
