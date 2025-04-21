package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.enums.RootFolderTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

@Data
@Builder
public class FileEventDto {

    private Path file;

    private Long rootFolderId;

    private FileEventTypeEnum fileEventTypeEnum;

    private RootFolderTypeEnum rootFolderTypeEnum;

    private RootFolderTypeEnum destFolderTypeEnum;
}
