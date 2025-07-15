package com.syncduo.server.model.rclone.core.transferred;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TransferStat {

    // 文件名称
    private String name;

    private long size;

    private long bytes;

    // if this transfer is checked only
    private boolean checked;

    // current timestamp
    private long timestamp;

    private String error;

    @JsonProperty("jobid")
    private long jobId;
}
