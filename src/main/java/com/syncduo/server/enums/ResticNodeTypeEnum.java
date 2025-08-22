package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@AllArgsConstructor
@Getter
public enum ResticNodeTypeEnum {

    DIRECTORY("dir"),

    FILE("file"),

    UNKNOWN("unknown");

    private final String type;

    public static ResticNodeTypeEnum fromString(String type) {
        for (ResticNodeTypeEnum value : values()) {
            if (Objects.equals(value.type, type)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
