package com.syncduo.server.model.rslsync.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FolderStoragePath {

    private int status;

    @JsonProperty("available_space")
    private long availableSpace;

    private String value; // folder storage path
}
