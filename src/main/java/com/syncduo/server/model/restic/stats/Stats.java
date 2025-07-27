package com.syncduo.server.model.restic.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;

@Data
public class Stats {
    @JsonProperty("total_size")
    private BigInteger totalSize; // bytes

    @JsonProperty("total_file_count")
    private BigInteger totalFileCount;

    @JsonProperty("total_blob_count")
    private BigInteger totalBlobCount;

    @JsonProperty("snapshots_count")
    private BigInteger snapshotsCount;

    @JsonProperty("total_uncompressed_size")
    private BigInteger totalUncompressedSize;

    @JsonProperty("compression_ratio")
    private long compressionRatio;

    @JsonProperty("compression_progress")
    private long compressionProgress;

    @JsonProperty("compression_space_saving")
    private long compressionSpaceSaving;
}
