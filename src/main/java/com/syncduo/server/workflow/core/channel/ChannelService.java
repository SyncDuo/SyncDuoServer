package com.syncduo.server.workflow.core.channel;

import com.syncduo.server.workflow.core.enums.ExecStatus;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChannelService {

    private final BlockingQueue<Message> queue = new ArrayBlockingQueue<>(100);

    public void sendFlowMessage(FlowContext flowContext, ExecStatus execStatus) {
        this.send(new Message(
                "FLOW",
                flowContext.getRegisterId(),
                flowContext.getFlowExecutionUuidV4(),
                null,
                null,
                execStatus,
                flowContext.getAllData(),
                ""
        ));
    }

    public void sendFlowMessage(FlowContext flowContext, ExecStatus execStatus, Exception e) {
        this.send(new Message(
                "FLOW",
                flowContext.getRegisterId(),
                flowContext.getFlowExecutionUuidV4(),
                null,
                null,
                execStatus,
                flowContext.getAllData(),
                e.toString()
        ));
    }

    public void sendNodeMessage(
            FlowContext flowContext,
            UUID nodeExecutionUuidV4,
            FlowNode node,
            ExecStatus execStatus) {
        this.send(new Message(
                "NODE",
                flowContext.getRegisterId(),
                flowContext.getFlowExecutionUuidV4(),
                nodeExecutionUuidV4,
                node,
                execStatus,
                flowContext.getAllData(),
                ""
        ));
    }

    public void sendNodeMessage(
            FlowContext flowContext,
            UUID nodeExecutionUuidV4,
            FlowNode node,
            ExecStatus execStatus,
            Exception e) {
        this.send(new Message(
                "NODE",
                flowContext.getRegisterId(),
                flowContext.getFlowExecutionUuidV4(),
                nodeExecutionUuidV4,
                node,
                execStatus,
                flowContext.getAllData(),
                e.toString()
        ));
    }

    private void send(Message msg) {
        // 重试三次, 每次等待 5 秒放入
        for (int i = 0; i < 3; i++) {
            try {
                if (this.queue.offer(msg, 5L, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Message take() throws InterruptedException {
        return queue.take();
    }
}
