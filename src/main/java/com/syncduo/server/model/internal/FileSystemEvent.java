package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.entity.FileEntity;
import lombok.Data;

import java.nio.file.Path;

@Data
public class FileSystemEvent {

    Long folderId;

    Path file;

    // file entity 在数据库, 但是不在文件系统的, 用于 fileEvent 等于 "删除" 的情况
    FileEntity fileEntityNotInFilesystem;

    FileEventTypeEnum fileEventTypeEnum;

    public FileSystemEvent(Long folderId, Path file, FileEventTypeEnum fileEventTypeEnum) {
        this.folderId = folderId;
        this.file = file;
        this.fileEventTypeEnum = fileEventTypeEnum;
    }

    public FileSystemEvent(Long folderId, FileEntity fileEntityNotInFilesystem, FileEventTypeEnum fileEventTypeEnum) {
        this.folderId = folderId;
        this.fileEntityNotInFilesystem = fileEntityNotInFilesystem;
        this.fileEventTypeEnum = fileEventTypeEnum;
    }
}
