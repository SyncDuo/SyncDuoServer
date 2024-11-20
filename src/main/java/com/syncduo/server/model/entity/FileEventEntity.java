package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file_event")
public class FileEventEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long fileEventId;

    private String fileEventType;

    private Long rootFolderId;

    private Long fileId;
}
