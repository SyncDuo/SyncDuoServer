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

    public void setFolderStats(Long fileCount, Long folderCount, Long space) {
        this.destFolderStats = new FolderStats(fileCount.toString(), folderCount.toString(), space.toString());
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


