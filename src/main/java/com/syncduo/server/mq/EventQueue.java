package com.syncduo.server.mq;

import com.syncduo.server.model.dto.event.FileEventDto;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class EventQueue {
    private final ConcurrentLinkedQueue<FileEventDto> sourceFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileEventDto> internalFolderEventQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<FileEventDto> contentFolderEventQueue = new ConcurrentLinkedQueue<>();

    public FileEventDto getSourceFolderEvent() {
        return this.sourceFolderEventQueue.poll();
    }

    public FileEventDto getInternalFolderEvent() {
        return this.internalFolderEventQueue.poll();
    }

    public FileEventDto getContentFolderEvent() {
        return this.contentFolderEventQueue.poll();
    }

    public void pushSourceEvent(FileEventDto fileEventDto) {
        this.sourceFolderEventQueue.offer(fileEventDto);
    }

    public void pushInternalEvent(FileEventDto fileEventDto) {
        this.internalFolderEventQueue.offer(fileEventDto);
    }

    public void pushContentEvent(FileEventDto fileEventDto) {
        this.contentFolderEventQueue.offer(fileEventDto);
    }
}
