package com.syncduo.server.model.restic.ls;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncduo.server.model.restic.snapshots.SnapshotSummary;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class Snapshot {
    /**
     * Always "snapshot"
     */
    @JsonProperty("message_type")
    private String messageType; // JSON 字段名: message_type

    /**
     * Always "snapshot" (deprecated)
     */
    @Deprecated
    @JsonProperty("message_type")
    private String structType; // JSON 字段名: struct_type

    /**
     * Timestamp of when the backup was started
     */
    private Instant time;

    /**
     * ID of the parent snapshot
     */
    private String parent;

    /**
     * ID of the root tree blob
     */
    private String tree;

    /**
     * List of paths included in the backup
     */
    private List<String> paths;

    /**
     * Hostname of the backed up machine
     */
    private String hostname;

    /**
     * Username the backup command was run as
     */
    private String username;

    /**
     * ID of owner (uint32)
     */
    private long uid;

    /**
     * ID of group (uint32)
     */
    private long gid;

    /**
     * List of paths and globs excluded from the backup
     */
    private List<String> excludes;

    /**
     * List of tags for the snapshot in question
     */
    private List<String> tags;

    /**
     * restic version used to create snapshot
     */
    @JsonProperty("program_version")
    private String programVersion; // JSON 字段名: program_version

    /**
     * Snapshot statistics
     */
    private SnapshotSummary summary;

    /**
     * Snapshot ID
     */
    private String id;

    /**
     * Snapshot ID, short form (deprecated)
     */
    @Deprecated
    @JsonProperty("short_id")
    private String shortId; // JSON 字段名: short_id

    public static String getCondition() {
        return "snapshot";
    }
}
