package com.syncduo.server.enums;

public enum SyncFlowTypeEnum {

    SOURCE_TO_INTERNAL,

    INTERNAL_TO_CONTENT;

    public static SyncFlowTypeEnum getByString(String syncFlowTypeString) {
        try {
            return SyncFlowTypeEnum.valueOf(syncFlowTypeString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
