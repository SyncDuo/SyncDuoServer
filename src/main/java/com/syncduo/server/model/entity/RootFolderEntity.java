package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("root_folder")
public class RootFolderEntity extends BaseEntity {

    @TableId
    private Long folderId;

    private String folderName;

    private String folderFullPath;

    private String folderType;
}
