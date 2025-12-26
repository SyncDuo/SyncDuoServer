package com.syncduo.server.workflow.node.restic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class Snapshot {

    @JsonProperty("time")
    private OffsetDateTime time;

    @JsonProperty("parent")
    private String parent;

    @JsonProperty("tree")
    private String tree;

    @JsonProperty("paths")
    private List<String> paths;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("username")
    private String username;

    @JsonProperty("uid")
    private Long uid;

    @JsonProperty("gid")
    private Long gid;

    @JsonProperty("excludes")
    private List<String> excludes;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("program_version")
    private String programVersion;

    @JsonProperty("summary")
    private SnapshotSummary summary;

    @JsonProperty("id")
    private String id;

    @Deprecated
    @JsonProperty("short_id")
    private String shortId;

    @Data
    @NoArgsConstructor
    public static  class SnapshotSummary {
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

    public BigInteger getFileCount() {
        return addBigIntegers(this.summary.filesNew, this.summary.filesChanged, this.summary.filesUnmodified);
    }

    public BigInteger getDirCount() {
        return addBigIntegers(this.summary.dirsNew, this.summary.dirsChanged, this.summary.dirsUnmodified);
    }

    public BigInteger getTotalBytes() {
        return this.summary.totalBytesProcessed;
    }

    private static BigInteger addBigIntegers(BigInteger... values) {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger value : values) {
            result = result.add(value);
        }
        return result;
    }
}
