package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CommonStatus implements Status{

    SUCCESS("SUCCESS"),

    FAILED("FAILED"),

    RUNNING("RUNNING"),
    ;

    private final String name;
}
