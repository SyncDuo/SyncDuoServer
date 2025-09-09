package com.syncduo.server.model.api.systeminfo;

import com.syncduo.server.model.api.global.FolderStats;
import lombok.Data;

import java.time.Duration;

@Data
public class SystemInfo {

    private int syncFlowNumber;

    private String fileCopyRate; // xx.xxMB/s(xx.xx MB copied in second)

    private FolderStats folderStats;

    private int watchers;

    private String uptime; // HH:MM

    public void setFileCopyRate(double fileCopyRate) {
        this.fileCopyRate = String.format("%.2f", fileCopyRate);
    }

    public void setUptime(long uptimeMillis) {
        Duration uptime = Duration.ofMillis(uptimeMillis);
        this.uptime = String.format("%02d:%02d", uptime.toHours(), uptime.toMinutes());
    }
}
