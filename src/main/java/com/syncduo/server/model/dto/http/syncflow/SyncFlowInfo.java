package com.syncduo.server.model.dto.http.syncflow;

import lombok.*;

@Data
@Builder
public class SyncFlowInfo {

    private String syncFlowId;

    private String syncFlowName;

    private String sourceFolderPath;

    private String destFolderPath;

    private String syncSettings;

    private String ignorePatten;

    private FolderStats destFolderStats;

    private String syncStatus;

    private String lastSyncTimeStamp;

    public void setFolderStats(String fileCount, String folderCount, String space) {
        this.destFolderStats = new FolderStats(fileCount, folderCount, space);
    }

    @AllArgsConstructor
    @ToString
    @Data
    static class FolderStats {
        private String fileCount;

        private String folderCount;

        private String space;
    }
}


