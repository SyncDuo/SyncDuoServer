package com.syncduo.server.enums;

import java.util.Arrays;
import java.util.List;

public enum SystemConfigEnum {

    SHADOW_FOLDER_STORAGE_PATH,

    ;

    public static List<SystemConfigEnum> getAllSystemConfigEnum() {
        return Arrays.stream(SystemConfigEnum.values()).toList();
    }
}
