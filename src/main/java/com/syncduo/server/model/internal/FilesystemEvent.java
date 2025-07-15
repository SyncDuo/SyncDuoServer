package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@AllArgsConstructor
@Data
public class FilesystemEvent {

    private Path folder;

    private Path file;

    private FileEventTypeEnum fileEventTypeEnum;
}
