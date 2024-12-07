package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum SyncSettingEnum {
    FLATTEN_FOLDER(1),

    MIRROR(0),
    ;

    private final int code;


    SyncSettingEnum(int code) {
        this.code = code;
    }

    public static SyncSettingEnum getByCode(int code) {
        for (SyncSettingEnum e : SyncSettingEnum.values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
