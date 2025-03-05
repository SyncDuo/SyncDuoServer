package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class RefilterEvent {

    SyncFlowEntity syncFlowEntity;

    FolderEntity sourceFolderEntity;

    FileEntity sourceFileEntity;

    FolderEntity destFolderEntity;

    FileEntity destFileEntity;

    FileEventTypeEnum fileEventTypeEnum;
}
