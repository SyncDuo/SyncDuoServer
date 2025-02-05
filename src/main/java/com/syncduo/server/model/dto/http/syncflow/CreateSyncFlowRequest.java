package com.syncduo.server.model.dto.http.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderName;

    private String destParentFolderFullPath;

    private String filterCriteria;

    private Boolean flattenFolder;

    private String syncFlowName;

    @JsonIgnore
    private String destFolderFullPath; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化
}
