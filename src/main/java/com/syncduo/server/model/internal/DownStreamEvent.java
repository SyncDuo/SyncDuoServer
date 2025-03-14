package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;


@Builder
@Data
public class DownStreamEvent {

    FolderEntity folderEntity;

    FileEntity fileEntity;

    Path file;

    // refilter event, 会带上 syncFlowEntity
    SyncFlowEntity syncFlowEntity;

    FileEventTypeEnum fileEventTypeEnum;
}
