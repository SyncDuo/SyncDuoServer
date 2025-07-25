package com.syncduo.server.model.api.filesystem;

import lombok.Data;

import java.nio.file.Path;

@Data
public class Folder {

    private String folderName;

    private String folderFullPath;

    public Folder(Path path) {
        this.folderName = path.getFileName().toString();
        this.folderFullPath = path.toAbsolutePath().toString();
    }
}
