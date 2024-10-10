package com.syncduo.server.model.dto.event;

import com.syncduo.server.enums.FileEventTypeEnum;
import lombok.Data;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@Data
public class FileEventDto {

    private Long fileEventId;

    private Long parentFileEventId;

    private Long folderId;

    private String relativePath;

    private Long fileId;

    private Path file;

    private BasicFileAttributes basicFileAttributes;

    private String fileMd5Checksum;

    private String uuid4;

    private FileEventTypeEnum fileEventType;
}
