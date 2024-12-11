package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum DeletedEnum {
    DELETED(1),

    NOT_DELETED(0),
    ;

    private final int code;

    DeletedEnum(int val) {
        this.code = val;
    }
}
