package com.syncduo.server.model.rclone.operations.stats;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class StatsRequest {

    private String fs;

    private String remote;

    private Map<String, Object> opt = new HashMap<>();

    // 默认仅查询文件夹
    public StatsRequest(String fs, String remote) {
        this.fs = fs;
        this.remote = remote;
        this.opt.put("dirsOnly", true);
    }
}
