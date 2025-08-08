package com.syncduo.server.model.api.snapshots;

import lombok.Data;

@Data
public class SnapshotsInfo {

    private String destFolderPath;

    private String startedAt; // timestamp

    private String finishedAt; // xx minutes ago

    private String snapshotId;

    private String snapshotSize; // MB

    private String backupFiles;

    private String backupJobStatus;

    private String backupErrorMessage;
}
