package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum FileDesyncEnum {
    FILE_DESYNC(1),

    FILE_SYNC(0),
    ;

    private final int code;
    FileDesyncEnum(int val) {
        this.code = val;
    }
}
