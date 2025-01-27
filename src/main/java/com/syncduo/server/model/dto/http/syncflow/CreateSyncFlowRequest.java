package com.syncduo.server.model.dto.http.syncflow;

import lombok.Data;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath;

    private String destFolderName;

    private String destParentFolderFullPath;

    private String filterCriteria;

    private Boolean flattenFolder;

    private String syncFlowName;
}
