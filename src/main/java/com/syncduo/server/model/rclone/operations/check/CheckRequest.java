package com.syncduo.server.model.rclone.operations.check;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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

    public CheckRequest(String srcFs, String dstFs) {
        this.srcFs = srcFs;
        this.dstFs = dstFs;
    }
}
