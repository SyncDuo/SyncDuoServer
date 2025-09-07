package com.syncduo.server.model.api.syncflow;

import lombok.Data;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath; // 由前端拼接而成

    private String filterCriteria;

    private String syncFlowName;
}
