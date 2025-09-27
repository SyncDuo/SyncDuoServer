package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RslsyncFolderStatus {
    DISCONNECT(0),

    CONNECT(2),

    UNKNOWN(-1),

    ;

    private final int syncLevel;

    public static RslsyncFolderStatus fromString(int syncLevel) {
        for (RslsyncFolderStatus value : values()) {
            if (value.syncLevel == syncLevel) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
