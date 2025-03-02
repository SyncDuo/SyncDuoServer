package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.entity.FileEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@Data
public class FileEvent {

    Long folderId;

    Path file;

    // file entity in database, but not in filesystem
    FileEntity fileEntityNotInFilesystem;

    FileEventTypeEnum fileEventTypeEnum;

    public FileEvent(Long folderId, Path file, FileEventTypeEnum fileEventTypeEnum) {
        this.folderId = folderId;
        this.file = file;
        this.fileEventTypeEnum = fileEventTypeEnum;
    }

    public FileEvent(Long folderId, FileEntity fileEntityNotInFilesystem, FileEventTypeEnum fileEventTypeEnum) {
        this.folderId = folderId;
        this.fileEntityNotInFilesystem = fileEntityNotInFilesystem;
        this.fileEventTypeEnum = fileEventTypeEnum;
    }
}
