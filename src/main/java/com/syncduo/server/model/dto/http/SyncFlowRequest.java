package com.syncduo.server.model.dto.http;

import lombok.Data;

@Data
public class SyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath;
}
