package com.syncduo.server.model.rclone.operations.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StatItem {

    @JsonProperty("Path")
    private String path;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Size")
    private long size;

    @JsonProperty("MimeType")
    private String mimeType;

    @JsonProperty("ModTime")
    private String modTime;

    @JsonProperty("IsDir")
    private boolean isDir;
}
