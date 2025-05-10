package com.syncduo.server.model.http.syncflow;

import com.syncduo.server.model.http.FolderStats;
import lombok.*;

@Data
@Builder
public class SyncFlowInfo {

    private String syncFlowId;

    private String syncFlowName;

    private String sourceFolderPath;

    private String destFolderPath;

    private String syncMode;

    private String ignorePatten;

    private String syncFlowType;

    private FolderStats destFolderStats;

    private String syncStatus;

    private String lastSyncTimeStamp;
}


