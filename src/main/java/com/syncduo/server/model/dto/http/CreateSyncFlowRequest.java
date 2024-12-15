package com.syncduo.server.model.dto.http;

import lombok.Data;

@Data
public class CreateSyncFlowRequest {

    private String sourceFolderFullPath;

    private String destFolderFullPath;

    // 如果 concat 为 true, 则 destFolderFullPath 需要拼接 sourceFolderFullPath 的名称
    // 如果 concat 为 false, 则不需要
    private Boolean concatDestFolderPath;

    private String filterCriteria;

    private Boolean flattenFolder;
}
