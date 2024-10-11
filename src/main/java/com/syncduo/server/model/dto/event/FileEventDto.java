package com.syncduo.server.model.dto.event;

import com.syncduo.server.enums.FileEventTypeEnum;
import lombok.Data;

import java.nio.file.Path;

@Data
public class FileEventDto {

    private Path file;

    private Long rootFolderId;

    private FileEventTypeEnum fileEventType;
}
