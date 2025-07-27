package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigInteger;
import java.sql.Timestamp;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("backup_job")
public class BackupJobEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long backupJobId;

    private String snapshotId;

    private Long syncFlowId;

    private Timestamp startedAt;

    private Timestamp finishedAt;

    private String backupJobStatus;

    private BigInteger backupFiles;

    private BigInteger backupBytes;

    private String errorMessage;

    private String successMessage;
}
