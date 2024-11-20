package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum SyncSettingEnum {
    FLATTEN_FOLDER(1),

    MIRROR(0),
    ;

    private final int code;
    SyncSettingEnum(int val) {
        this.code = val;
    }
}
