package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("root_folder")
public class RootFolderEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long rootFolderId;

    private String rootFolderName;

    private String rootFolderFullPath;

    private String rootFolderType;

    @TableField(fill = FieldFill.INSERT)
    private int folderDeleted;
}
