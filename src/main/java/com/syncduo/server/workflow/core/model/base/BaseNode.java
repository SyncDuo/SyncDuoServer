package com.syncduo.server.workflow.core.model.base;

import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseNode {
    public abstract NodeResult execute(FlowContext context);

    protected void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("节点执行被中断");
        }
    }
}
