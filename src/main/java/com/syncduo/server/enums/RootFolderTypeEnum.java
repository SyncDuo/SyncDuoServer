package com.syncduo.server.enums;

public enum RootFolderTypeEnum {

    SOURCE_FOLDER,

    INTERNAL_FOLDER,

    CONTENT_FOLDER;

    public static RootFolderTypeEnum getByString(String rootFolderTypeString) {
        try {
            return RootFolderTypeEnum.valueOf(rootFolderTypeString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
