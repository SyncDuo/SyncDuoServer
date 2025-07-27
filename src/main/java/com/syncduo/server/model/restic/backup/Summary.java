package com.syncduo.server.model.restic.backup;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

@Data
public class Summary {
    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("dry_run")
    private boolean dryRun;

    @JsonProperty("files_new")
    private BigInteger filesNew;

    @JsonProperty("files_changed")
    private BigInteger filesChanged;

    @JsonProperty("files_unmodified")
    private BigInteger filesUnmodified;

    @JsonProperty("dirs_new")
    private BigInteger dirsNew;

    @JsonProperty("dirs_changed")
    private BigInteger dirsChanged;

    @JsonProperty("dirs_unmodified")
    private BigInteger dirsUnmodified;

    @JsonProperty("data_blobs")
    private long dataBlobs;

    @JsonProperty("tree_blobs")
    private long treeBlobs;

    @JsonProperty("data_added")
    private BigInteger dataAdded;

    @JsonProperty("data_added_packed")
    private BigInteger dataAddedPacked;

    @JsonProperty("total_files_processed")
    private BigInteger totalFilesProcessed;

    @JsonProperty("total_bytes_processed")
    private BigInteger totalBytesProcessed;

    @JsonProperty("backup_start")
    private Instant backupStart;

    @JsonProperty("backup_end")
    private Instant backupEnd;

    @JsonProperty("total_duration")
    private double totalDuration;

    @JsonProperty("snapshot_id")
    private String snapshotId; // field is omitted if snapshot creation was skipped.

    public static String getCondition() {
        return "summary";
    }
}
