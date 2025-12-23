package com.syncduo.server.workflow.core.model.definition;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class FlowDefinition {
    private final String name;
    private List<FlowNode> nodes = new ArrayList<>();

    public FlowDefinition(String name, List<FlowNode> nodeList) {
        this.name = name;
        this.nodes = nodeList;
    }

    public void addNodes(FlowNode... nodes) {
        Set<String> nodeIds = this.nodes.stream().map(FlowNode::getNodeId).collect(Collectors.toSet());
        for (FlowNode node : nodes) {
            // 检测是否有重复 node id
            if (nodeIds.contains(node.getNodeId())) {
                throw new IllegalArgumentException(
                        "FlowDefinition:" + name + " 中存在重复的Node ID: " + node.getNodeId()
                );
            }
            this.nodes.add(node);
            nodeIds.add(node.getNodeId());
        }
    }
}
