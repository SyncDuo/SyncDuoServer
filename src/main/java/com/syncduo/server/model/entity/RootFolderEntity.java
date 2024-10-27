package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
