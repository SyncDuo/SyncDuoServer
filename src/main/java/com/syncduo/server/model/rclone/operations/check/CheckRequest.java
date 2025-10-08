package com.syncduo.server.model.rclone.operations.check;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncduo.server.model.rclone.global.Config;
import com.syncduo.server.model.rclone.global.Filter;
import lombok.Data;

import java.util.List;

@Data
public class CheckRequest {

    private String srcFs;

    private String dstFs;

    @JsonProperty("oneway") // known issue in rclone operations/check api
    private boolean oneWay = true;

    private boolean combined = false;

    private boolean missingOnSrc = false;

    private boolean missingOnDst = false;

    private boolean differ = false;

    @JsonProperty("_filter")
    private final Filter filter = new Filter();

    @JsonProperty("_config")
    private final Config config = new Config();

    public CheckRequest(String srcFs, String dstFs) {
        this.srcFs = srcFs;
        this.dstFs = dstFs;
    }

    public void exclude(List<String> excludeList) {
        this.filter.setExcludeRule(excludeList);
    }
}
