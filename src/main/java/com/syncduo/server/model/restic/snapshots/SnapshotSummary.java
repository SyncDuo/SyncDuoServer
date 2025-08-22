package com.syncduo.server.model.restic.snapshots;

import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

@Data
public class SnapshotSummary {
    /**
     * Time at which the backup was started
     */
    private Instant backupStart;

    /**
     * Time at which the backup was completed
     */
    private Instant backupEnd;

    /**
     * Number of new files (uint64)
     */
    private BigInteger filesNew;

    /**
     * Number of files that changed (uint64)
     */
    private BigInteger filesChanged;

    /**
     * Number of files that did not change (uint64)
     */
    private BigInteger filesUnmodified;

    /**
     * Number of new directories (uint64)
     */
    private BigInteger dirsNew;

    /**
     * Number of directories that changed (uint64)
     */
    private BigInteger dirsChanged;

    /**
     * Number of directories that did not change (uint64)
     */
    private BigInteger dirsUnmodified;

    /**
     * Number of data blobs added (int64)
     */
    private long dataBlobs;

    /**
     * Number of tree blobs added (int64)
     */
    private long treeBlobs;

    /**
     * Amount of (uncompressed) data added, in bytes (uint64)
     */
    private BigInteger dataAdded;

    /**
     * Amount of data added (after compression), in bytes (uint64)
     */
    private BigInteger dataAddedPacked;

    /**
     * Total number of files processed (uint64)
     */
    private BigInteger totalFilesProcessed;

    /**
     * Total number of bytes processed (uint64)
     */
    private BigInteger totalBytesProcessed;

}
