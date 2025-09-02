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
@TableName("restore_job")
public class RestoreJobEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long restoreJobId;

    private String restoreJobStatus;

    private String errorMessage;

    private BigInteger secondsElapsed;

    private String restoreRootPath;

    private BigInteger restoreFiles;

    private BigInteger restoreBytes;

    private String snapshotId;
}
