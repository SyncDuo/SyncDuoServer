package com.syncduo.server.model.restic.restore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RestoreError {

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("error.message")
    private String errorMessage;

    @JsonProperty("during")
    private String during;

    @JsonProperty("item")
    private String item;

    public static String getCondition() {
        return "error";
    }
}
