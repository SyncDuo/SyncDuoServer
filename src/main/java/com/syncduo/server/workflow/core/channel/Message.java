package com.syncduo.server.workflow.core.channel;

import com.syncduo.server.workflow.core.enums.ExecStatus;
import com.syncduo.server.workflow.core.model.definition.FlowNode;

import java.util.Map;
import java.util.UUID;

/**
 * @param type FLOW 表示 flow 执行信息, NODE 表示 node 执行信息
 */

public record Message(
        String type,
        Long registerId,
        UUID flowExecutionUuidV4,
        UUID nodeExecutionUuidV4,
        FlowNode node,
        ExecStatus execStatus,
        Map<String, Object> data,
        String errorMessage) {}
