package com.syncduo.server.model.restic.init;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Init {

    @JsonProperty("message_type")
    private String messageType;

    private String id;

    private String repository;

    public static String getCondition() {
        return "initialized";
    }
}
