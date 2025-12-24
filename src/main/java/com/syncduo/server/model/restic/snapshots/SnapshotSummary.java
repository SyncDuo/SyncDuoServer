package com.syncduo.server.model.restic.snapshots;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class SnapshotSummary {
    /**
     * 备份开始时间
     */
    @JsonProperty("backup_start")
    private OffsetDateTime backupStart;

    /**
     * 备份结束时间
     */
    @JsonProperty("backup_end")
    private OffsetDateTime backupEnd;

    /**
     * 新文件数量
     */
    @JsonProperty("files_new")
    private BigInteger filesNew;

    /**
     * 已更改文件数量
     */
    @JsonProperty("files_changed")
    private BigInteger filesChanged;

    /**
     * 未修改文件数量
     */
    @JsonProperty("files_unmodified")
    private BigInteger filesUnmodified;

    /**
     * 新目录数量
     */
    @JsonProperty("dirs_new")
    private BigInteger dirsNew;

    /**
     * 已更改目录数量
     */
    @JsonProperty("dirs_changed")
    private BigInteger dirsChanged;

    /**
     * 未修改目录数量
     */
    @JsonProperty("dirs_unmodified")
    private BigInteger dirsUnmodified;

    /**
     * 添加的数据块数量
     */
    @JsonProperty("data_blobs")
    private Long dataBlobs;

    /**
     * 添加的树块数量
     */
    @JsonProperty("tree_blobs")
    private Long treeBlobs;

    /**
     * 添加的数据量（未压缩），单位：字节
     */
    @JsonProperty("data_added")
    private BigInteger dataAdded;

    /**
     * 添加的数据量（压缩后），单位：字节
     */
    @JsonProperty("data_added_packed")
    private BigInteger dataAddedPacked;

    /**
     * 处理的总文件数
     */
    @JsonProperty("total_files_processed")
    private BigInteger totalFilesProcessed;

    /**
     * 处理的总字节数
     */
    @JsonProperty("total_bytes_processed")
    private BigInteger totalBytesProcessed;

}
