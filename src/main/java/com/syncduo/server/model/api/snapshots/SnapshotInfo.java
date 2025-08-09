package com.syncduo.server.model.api.snapshots;

import lombok.Data;

@Data
public class SnapshotInfo {

    private String startedAt; // timestamp

    private String finishedAt; // timestamp

    private String snapshotId;

    private String snapshotSize; // MB

    private String backupFiles;

    private String backupJobStatus;

    private String backupErrorMessage;
}
