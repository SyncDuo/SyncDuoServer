package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum SyncSettingEnum {
    FLATTEN_FOLDER(1, true),

    MIRROR(0, false),
    ;

    private final int code;

    private final boolean value;


    SyncSettingEnum(int code, boolean value) {
        this.code = code;
        this.value = value;
    }
}
