package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("copy_job")
public class CopyJobEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long copyJobId;

    private Long rcloneJobId;

    private Long syncFlowId;

    private Timestamp startedAt;

    private Timestamp finishedAt;

    private String copyJobStatus;

    private Long transferredFiles;

    private Long transferredBytes;

    private String errorMessage;
}
