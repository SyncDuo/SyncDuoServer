package com.syncduo.server.model.http.systeminfo;

import com.syncduo.server.model.http.FolderStats;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

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

    public void setUptime() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = runtimeMXBean.getUptime();
        Duration uptime = Duration.ofMillis(uptimeMillis);

        long hours = uptime.toHours();
        long minutes = uptime.minusHours(hours).toMinutes();

        this.setUptime(String.format("%02d:%02d", hours, minutes));
    }
}
