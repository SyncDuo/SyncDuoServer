package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RslsyncFolderSyncLevel {
    DISCONNECT(0),

    SELECTIVE_SYNC(1),

    SYNCED(2),

    UNKNOWN(-1),

    ;

    private final int syncLevel;

    public static RslsyncFolderSyncLevel fromCode(int syncLevel) {
        for (RslsyncFolderSyncLevel value : values()) {
            if (value.syncLevel == syncLevel) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
