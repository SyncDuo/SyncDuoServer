package com.syncduo.server.model.dto.event;

import lombok.Data;

import java.nio.file.attribute.BasicFileAttributes;

@Data
public class FileEventDto {

    private Long fileEventId;

    private Long parentFileEventId;

    private Long folderId;

    private Long fileId;

    private BasicFileAttributes basicFileAttributes;

    private String fileMd5Checksum;
}
