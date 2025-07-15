package com.syncduo.server.model.api.systeminfo;

import com.syncduo.server.model.api.FolderStats;
import lombok.Data;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;

@Data
public class SystemInfoResponse {

    private int code;

    private String message;

    private String hostName;

    private int syncFlowNumber;

    private String fileCopyRate; // xx.xxMB/s(xx.xx MB copied in second)

    private FolderStats folderStats;

    private int watchers;

    private String uptime; // HH:MM

    public SystemInfoResponse onSuccess(String message) {
        this.setCode(200);
        this.setMessage(message);
        return this;
    }

    public SystemInfoResponse onFailed(String message) {
        this.setCode(500);
        this.setMessage(message);
        return this;
    }

    public void setFileCopyRate(double fileCopyRate) {
        this.fileCopyRate = String.format("%.2f", fileCopyRate);
    }

    public void setUptime(long uptimeMillis) {
        Duration uptime = Duration.ofMillis(uptimeMillis);
        this.uptime = String.format("%02d:%02d", uptime.toHours(), uptime.toMinutes());
    }
}
