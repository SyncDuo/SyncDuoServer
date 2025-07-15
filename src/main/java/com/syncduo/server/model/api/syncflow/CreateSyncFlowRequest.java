package com.syncduo.server.model.api.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath; // 由前端拼接而成

    private String filterCriteria;

    private String syncFlowName;
}
