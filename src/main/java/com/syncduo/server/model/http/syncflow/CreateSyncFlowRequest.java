package com.syncduo.server.model.http.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncModeEnum;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath; // 由前端拼接而成

    private String filterCriteria;

    private String syncMode;

    private String syncFlowName;

    private String syncFlowType; // 当 syncflow type = sync 时, sync setting 和 filterCriteria 失效

    @JsonIgnore
    private Path sourceFolder; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private Path destFolder; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private SyncFlowTypeEnum syncFlowTypeEnum; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private SyncModeEnum syncModeEnum; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private List<String> filters; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化
}
