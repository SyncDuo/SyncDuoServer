package com.syncduo.server.model.rclone.core.stat;

import lombok.Data;

@Data
public class CoreStatsRequest {
    private String group;

    public CoreStatsRequest(long jobId) {
        this.group = "job/%s".formatted(jobId);
    }

    public CoreStatsRequest() {
        this.group = "";
    }
}
