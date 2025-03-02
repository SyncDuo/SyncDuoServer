package com.syncduo.server.enums;

public enum SyncFlowTypeEnum {

    TRANSFORM,

    SYNC;

    public static SyncFlowTypeEnum getByString(String syncFlowTypeString) {
        try {
            return SyncFlowTypeEnum.valueOf(syncFlowTypeString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
