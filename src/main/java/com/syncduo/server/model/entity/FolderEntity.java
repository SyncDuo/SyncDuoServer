package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("folder")
public class FolderEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long folderId;

    private String folderName;

    // always starts with "/"
    // always ends with "folder name"
    // eg: /root/folder1
    private String folderFullPath;
}
