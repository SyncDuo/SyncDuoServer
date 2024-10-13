package com.syncduo.server.mq.consumer;

import com.syncduo.server.mq.SystemQueue;
import com.syncduo.server.service.impl.FileOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FileOpTaskHandler {

    @Value("${syncduo.server.event.polling.num:10}")
    private Integer pollingNum;

    private final SystemQueue systemQueue;

    private final FileOperationService fileOperationService;

    @Autowired
    public FileOpTaskHandler(SystemQueue systemQueue, FileOperationService fileOperationService) {
        this.systemQueue = systemQueue;
        this.fileOperationService = fileOperationService;
    }
}
