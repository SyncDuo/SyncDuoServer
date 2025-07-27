package com.syncduo.server.model.restic.global;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ExitErrors {

    @JsonProperty("message_type")
    private String messageType;

    private int code;

    private String message;

    public static String getCondition() {
        return "exit_error";
    }
}
