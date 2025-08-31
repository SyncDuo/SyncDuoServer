package com.syncduo.server.model.restic.backup;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BackupError {
    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("error.message")
    private String errorMessage;

    @JsonProperty("during")
    private String during; // What restic was trying to do

    @JsonProperty("item")
    private String item; // Usually, the path of the problematic file

    public static String getCondition() {
        return "error";
    }
}
