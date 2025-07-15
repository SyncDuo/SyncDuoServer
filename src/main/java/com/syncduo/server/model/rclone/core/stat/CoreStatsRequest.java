package com.syncduo.server.model.rclone.core.stat;

import lombok.Data;

@Data
public class CoreStatsRequest {
    private String group;

    public void setGroup(long jobId) {
        this.group = "job/%s".formatted(jobId);
    }

    public CoreStatsRequest(String group) {
        this.group = group;
    }

    public CoreStatsRequest(long jobId) {
        this.setGroup(jobId);
    }
}
