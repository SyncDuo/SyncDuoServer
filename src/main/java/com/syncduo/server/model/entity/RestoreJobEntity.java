package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigInteger;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("restore_job")
public class RestoreJobEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long restoreJobId;

    private String restoreJobStatus;

    private String errorMessage;

    private BigInteger secondsElapsed;

    private String originFilePath; // 当一次 restore 多个文件时, 这个字段为空

    private String restoreRootPath;

    private String restoreFullPath;

    private BigInteger restoreFiles;

    private BigInteger restoreBytes;

    private String snapshotId;
}
