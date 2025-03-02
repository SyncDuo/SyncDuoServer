package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FolderEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;


@AllArgsConstructor
@Data
public class DownStreamEvent {

    FolderEntity folderEntity;

    FileEntity fileEntity;

    Path file;

    FileEventTypeEnum fileEventTypeEnum;
}
