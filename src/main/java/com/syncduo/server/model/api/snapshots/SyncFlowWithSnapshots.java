package com.syncduo.server.model.api.snapshots;

import lombok.Data;

import java.util.List;

@Data
public class SyncFlowWithSnapshots {

    private String syncFlowId;

    private String syncFlowName;

    private String sourceFolderPath;

    private String destFolderPath;

    private List<SnapshotInfo> snapshotInfoList;
}
