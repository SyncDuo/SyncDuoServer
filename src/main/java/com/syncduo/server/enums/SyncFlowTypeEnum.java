package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncFlowTypeEnum {

    REACTIVE_SYNC("REACTIVE_SYNC"),

    BACKUP_ONLY("BACKUP_ONLY"),

    UNKNOWN("UNKNOWN"),

    ;

    private final String type;

    public static SyncFlowTypeEnum fromTypeString(String type) {
        for (SyncFlowTypeEnum value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
