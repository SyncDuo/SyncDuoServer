package com.syncduo.server.enums;

import lombok.Data;
import lombok.Getter;

@Getter
public enum FileDeletedEnum {
    FILE_DELETED(1),

    FILE_NOT_DELETED(0),
    ;

    private final int code;
    FileDeletedEnum(int val) {
        this.code = val;
    }
}
