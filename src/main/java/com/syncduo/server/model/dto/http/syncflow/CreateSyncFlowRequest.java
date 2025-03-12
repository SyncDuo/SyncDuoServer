package com.syncduo.server.model.dto.http.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncSettingEnum;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath; // 由前端拼接而成

    private String filterCriteria;

    private String syncSetting;

    private String syncFlowName;

    private String syncFlowType;

    @JsonIgnore
    private Path sourceFolder; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private Path destFolder; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private SyncFlowTypeEnum syncFlowTypeEnum; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private SyncSettingEnum syncSettingEnum; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化

    @JsonIgnore
    private List<String> filters; // 这个变量用于内部业务逻辑使用, 不参与序列化/反序列化
}
