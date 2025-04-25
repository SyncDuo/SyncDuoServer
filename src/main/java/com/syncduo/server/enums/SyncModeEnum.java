package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum SyncModeEnum {
    FLATTEN_FOLDER(1),

    MIRROR(0),
    ;

    private final int code;


    SyncModeEnum(int code) {
        this.code = code;
    }

    public static SyncModeEnum getByCode(int code) {
        for (SyncModeEnum e : SyncModeEnum.values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
