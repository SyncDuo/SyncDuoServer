package com.syncduo.server.workflow.model.db;

import com.baomidou.mybatisplus.annotation.*;
import com.syncduo.server.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigInteger;
import java.sql.Timestamp;

/**
 * 快照元数据表实体类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TableName("snapshot_meta")
public class SnapshotMeta extends BaseEntity {
    /**
     * 快照元数据ID，主键
     */
    @TableId(value = "snapshot_meta_id", type = IdType.AUTO)
    private Long snapshotMetaId;

    /**
     * 源目录路径
     */
    @TableField("source_directory")
    private String sourceDirectory;

    /**
     * 备份仓库路径
     */
    @TableField("backup_repository")
    private String backupRepository;

    /**
     * 快照创建时间（来自restic）
     */
    @TableField("created_at")
    private Timestamp createdAt;

    /**
     * 快照ID
     */
    @TableField("snapshot_id")
    private String snapshotId;

    /**
     * 文件数量
     */
    @TableField("file_count")
    private BigInteger fileCount;

    /**
     * 文件夹数量
     */
    @TableField("dir_count")
    private BigInteger dirCount;

    /**
     * 快照大小（字节）
     */
    @TableField("snapshot_size_bytes")
    private BigInteger snapshotSizeBytes;

    /**
     * 主机名
     */
    @TableField("hostname")
    private String hostname;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;
}
