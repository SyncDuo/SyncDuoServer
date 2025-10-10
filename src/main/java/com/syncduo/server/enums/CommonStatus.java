package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CommonStatus implements Status{

    SUCCESS("SUCCESS"),

    FAILED("FAILED"),

    RUNNING("RUNNING"),

    UNKNOWN("UNKNOWN")
    ;

    private final String name;

    public static CommonStatus fromName(String name) {
        for (CommonStatus value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
