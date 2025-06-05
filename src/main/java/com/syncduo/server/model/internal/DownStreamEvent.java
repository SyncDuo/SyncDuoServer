package com.syncduo.server.model.internal;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;


@Builder
@Data
public class DownStreamEvent {

    FolderEntity folderEntity;

    FileEntity fileEntity;

    // 如果事件不是从 watcher 触发, 则没有 file
    Path file;

    // 如果该变量为空, 则由 downStreamHandler 填充, 表示处理全部下游
    SyncFlowEntity syncFlowEntity;

    FileEventTypeEnum fileEventTypeEnum;

    public DownStreamEvent(DownStreamEvent event) {
        this.folderEntity = event.getFolderEntity();
        this.fileEntity = event.getFileEntity();
        this.file = event.getFile();
        this.syncFlowEntity = null;
        this.fileEventTypeEnum = event.getFileEventTypeEnum();
    }
}
