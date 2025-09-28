package com.syncduo.server.model.rclone.core.stat;

import lombok.Data;

import java.util.List;

@Data
public class CoreStatsResponse {

    // total transferred bytes
    private long bytes;

    // total checked files
    private long checks;

    // total deleted files
    private long deletes;

    // elapsed time floating point in second since rclone up. eg: 0.000019366
    private String elapsedTime;

    // total errors
    private long errors;

    // estimated time in seconds until the group completes.eg: 0.000019366. always null
    private String eta;

    // whether there has at least one fatal error
    private boolean fatalError;

    // last error as string
    private Exception lastError;

    // total files renamed
    private long renames;

    // whether there has at one non-retry error
    private boolean retryError;

    // total files copy happen "only" in remote and not using local resources
    private long serverSideCopies;

    // total bytes copy happen "only" in remote and not using local resources
    private long serverSideCopyBytes;

    // total files move happen "only" in remote and not using local resources
    private long serverSideMoves;

    // total bytes move happen "only" in remote and not using local resources
    private long serverSideMoveBytes;

    // bytes in seconds
    private double speed;

    // total bytes "plans" to transferred
    private long totalBytes;

    // total files "plans" to check
    private long totalChecks;

    // total files "plans" to transferred
    private long totalTransfers;

    // total time spend actually transfer
    private double transferTime;

    // total files actually transfer
    private long transfers;

    // transferring job stat
    private List<TransferringJobStat> transferring;

    // list of files name of currently checking files
    private List<String> checking;
}
