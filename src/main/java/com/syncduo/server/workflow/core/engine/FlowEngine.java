package com.syncduo.server.workflow.core.engine;

import com.syncduo.server.workflow.core.channel.ChannelService;
import com.syncduo.server.workflow.core.enums.ExecStatus;
import com.syncduo.server.workflow.core.enums.ParamSourceType;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.definition.FlowDefinition;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.core.model.definition.ParamValue;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@Slf4j
@RequiredArgsConstructor
public class FlowEngine {

    private final FlowValidator flowValidator;

    private final ChannelService mq;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Future<?> execute(Long registerId, FlowDefinition flowDefinition) {
        // 拓扑排序
        List<FlowNode> sortedNodes = this.flowValidator.topologicalSort(flowDefinition);
        // 初始参数放入 context
        FlowContext flowContext = this.createFlowContextFromNodes(registerId, flowDefinition);
        // 开始执行
        return executor.submit(() -> this.executeFlow(sortedNodes, flowContext));
    }

    private void executeFlow(List<FlowNode> sortedNodes, FlowContext flowContext) {
        // Flow 执行开始
        this.mq.sendFlowMessage(flowContext, ExecStatus.RUNNING);
        UUID nodeExecutionUuidV4 = UUID.randomUUID();
        for (FlowNode sortedNode : sortedNodes) {
            try {
                // Node 开始执行
                this.mq.sendNodeMessage(
                        flowContext,
                        nodeExecutionUuidV4,
                        sortedNode,
                        ExecStatus.RUNNING
                );
                // 获取 node 对应的实现
                BaseNode node = this.flowValidator.getNodeByName(sortedNode.getName());
                // Node 执行开始
                NodeResult nodeResult = node.execute(flowContext);
                // Node 执行结束
                if (nodeResult.getExecStatus().equals(ExecStatus.FAILED)) {
                    throw new Exception("node execute failed, error is %s".formatted(nodeResult.getError()));
                }
                // 将 node 的输出放入 context
                flowContext.putAll(nodeResult);
                this.mq.sendNodeMessage(
                        flowContext,
                        nodeExecutionUuidV4,
                        sortedNode,
                        ExecStatus.SUCCESS
                );
                // 刷新 nodeExecutionUuidV4
                nodeExecutionUuidV4 = UUID.randomUUID();
            } catch (Exception e) {
                this.mq.sendNodeMessage(
                        flowContext,
                        nodeExecutionUuidV4,
                        sortedNode,
                        ExecStatus.FAILED,
                        e
                );
                this.mq.sendFlowMessage(flowContext, ExecStatus.FAILED, e);
                return;
            }
        }
        this.mq.sendFlowMessage(flowContext, ExecStatus.SUCCESS);
    }

    private FlowContext createFlowContextFromNodes(Long registerId, FlowDefinition flowDefinition) {
        FlowContext result = new FlowContext(registerId, flowDefinition.getName());
        for (FlowNode flowNode : flowDefinition.getNodes()) {
            Map<String, ParamValue> inputParams = flowNode.getInputParams();
            inputParams.forEach((k, v) -> {
                if (v.getParamSourceType().equals(ParamSourceType.MANUAL)) {
                    result.put(k , v.getValue());
                }
            });
        }
        return result;
    }
}
