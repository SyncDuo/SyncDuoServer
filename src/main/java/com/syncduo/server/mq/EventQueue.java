package com.syncduo.server.mq;

import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.dto.mq.FileMessageDto;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class EventQueue {
    private final ConcurrentLinkedQueue<FileEventDto> sourceFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileMessageDto> internalFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileMessageDto> contentFolderEventQueue = new ConcurrentLinkedQueue<>();

    public FileEventDto pollSourceFolderEvent() {
        return this.sourceFolderEventQueue.poll();
    }

    public FileMessageDto pollInternalFolderEvent() {
        return this.internalFolderEventQueue.poll();
    }

    public FileMessageDto pollContentFolderEvent() {
        return this.contentFolderEventQueue.poll();
    }

    public void pushSourceEvent(FileEventDto fileEventDto) {
        this.sourceFolderEventQueue.offer(fileEventDto);
    }

    public void pushInternalEvent(FileMessageDto fileMessageDto) {
        this.internalFolderEventQueue.offer(fileMessageDto);
    }

    public void pushContentEvent(FileMessageDto fileMessageDto) {
        this.contentFolderEventQueue.offer(fileMessageDto);
    }
}
