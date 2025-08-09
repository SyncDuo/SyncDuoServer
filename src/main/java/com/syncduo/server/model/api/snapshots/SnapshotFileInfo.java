package com.syncduo.server.model.api.snapshots;

import lombok.Data;

@Data
public class SnapshotFileInfo {

    String fileName;

    String lastModifiedTime;

    int size;

    int files;

    int folders;

    boolean isFolder;

    public static SnapshotFileInfo folder(
            String fileName,
            String lastModifiedTime,
            int size,
            int files,
            int folders) {
        SnapshotFileInfo fileInfo = new SnapshotFileInfo();
        fileInfo.fileName = fileName;
        fileInfo.lastModifiedTime = lastModifiedTime;
        fileInfo.size = size;
        fileInfo.files = files;
        fileInfo.folders = folders;
        fileInfo.isFolder = true;

        return fileInfo;
    }

    public static SnapshotFileInfo file(
            String fileName,
            String lastModifiedTime,
            int size) {
        SnapshotFileInfo fileInfo = new SnapshotFileInfo();
        fileInfo.fileName = fileName;
        fileInfo.lastModifiedTime = lastModifiedTime;
        fileInfo.size = size;
        fileInfo.isFolder = false;

        return fileInfo;
    }
}
