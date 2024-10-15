package com.syncduo.server.mq.producer;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.mq.SystemQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileOpTaskProducer {
    private final SystemQueue systemQueue;

    @Autowired
    public FileOpTaskProducer(SystemQueue systemQueue) {
        this.systemQueue = systemQueue;
    }

}
