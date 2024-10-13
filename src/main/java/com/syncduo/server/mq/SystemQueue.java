package com.syncduo.server.mq;

import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.dto.mq.FileMsgDto;
import com.syncduo.server.model.dto.mq.FileOpTaskDto;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class SystemQueue {
    private final ConcurrentLinkedQueue<FileEventDto> sourceFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileMsgDto> internalFileMsgQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileMsgDto> contentFileMsgQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileOpTaskDto> fileOpTaskQueue = new ConcurrentLinkedQueue<>();

    public FileEventDto pollSourceFolderEvent() {
        return this.sourceFolderEventQueue.poll();
    }

    public FileMsgDto pollInternalFileMsg() {
        return this.internalFileMsgQueue.poll();
    }

    public FileMsgDto pollContentFileMsg() {
        return this.contentFileMsgQueue.poll();
    }

    public FileOpTaskDto pollFileOpTask() {
        return this.fileOpTaskQueue.poll();
    }

    public void pushSourceEvent(FileEventDto fileEventDto) {
        this.sourceFolderEventQueue.offer(fileEventDto);
    }

    public void pushInternalFileMsg(FileMsgDto fileMsgDto) {
        this.internalFileMsgQueue.offer(fileMsgDto);
    }

    public void pushContentFileMsg(FileMsgDto fileMsgDto) {
        this.contentFileMsgQueue.offer(fileMsgDto);
    }

    public void pushFileOpTask(FileOpTaskDto fileOperationMsg) {
        this.fileOpTaskQueue.offer(fileOperationMsg);
    }
}
