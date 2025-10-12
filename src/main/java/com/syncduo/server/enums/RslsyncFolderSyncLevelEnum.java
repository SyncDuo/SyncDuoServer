package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RslsyncFolderSyncLevelEnum {
    DISCONNECT(0),

    SELECTIVE_SYNC(1),

    SYNCED(2),

    UNKNOWN(-1),

    ;

    private final int syncLevel;

    public static RslsyncFolderSyncLevelEnum fromCode(int syncLevel) {
        for (RslsyncFolderSyncLevelEnum value : values()) {
            if (value.syncLevel == syncLevel) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
