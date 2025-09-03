package com.syncduo.server.model.rclone.sync.copy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncduo.server.model.rclone.global.Filter;
import lombok.Data;

import java.util.List;

@Data
public class SyncCopyRequest {

    private String srcFs;

    private String dstFs;

    // 是否在 dstFs 创建空的 dir, 对应 srcFs
    private boolean createEmptySrcDirs = true;

    @JsonProperty("_filter")
    private final Filter filter = new Filter();

    public SyncCopyRequest(String srcFs, String dstFs) {
        this.srcFs = srcFs;
        this.dstFs = dstFs;
    }

    public void exclude(List<String> excludeList) {
        this.filter.setExcludeRule(excludeList);
    }
}
