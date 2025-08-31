package com.syncduo.server.model.api.syncflow;

import com.syncduo.server.model.api.global.FolderStats;
import com.syncduo.server.model.entity.SyncFlowEntity;
import lombok.*;
import org.apache.commons.lang3.ObjectUtils;

@Data
public class SyncFlowInfo {

    private String syncFlowId;

    private String syncFlowName;

    private String sourceFolderPath;

    private String destFolderPath;

    private String syncStatus;

    private String lastSyncTimeStamp = "";

    private String ignorePatten;

    private FolderStats destFolderStats;

    public SyncFlowInfo(SyncFlowEntity syncFlowEntity) {
        this.syncFlowId = syncFlowEntity.getSyncFlowId().toString();
    }

    public SyncFlowInfo(SyncFlowEntity syncFlowEntity, FolderStats folderStats) {
        this.syncFlowId = syncFlowEntity.getSyncFlowId().toString();
        this.syncFlowName = syncFlowEntity.getSyncFlowName();
        this.sourceFolderPath = syncFlowEntity.getSourceFolderPath();
        this.destFolderPath = syncFlowEntity.getDestFolderPath();
        this.syncStatus = syncFlowEntity.getSyncStatus();
        if (ObjectUtils.isNotEmpty(syncFlowEntity.getLastSyncTime())) {
            this.lastSyncTimeStamp = syncFlowEntity.getLastSyncTime().toString();
        }
        this.ignorePatten = syncFlowEntity.getFilterCriteria();
        this.destFolderStats = folderStats;
    }
}


