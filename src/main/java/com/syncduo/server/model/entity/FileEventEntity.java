package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file_event")
public class FileEventEntity extends BaseEntity {

    @TableId
    private Long fileEventId;

    private Long parentFileEventId;

    private String fileEventType;

    private Long folderId;

    private Long fileId;
}
