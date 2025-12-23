package com.syncduo.server.workflow.core.engine;

import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.exception.*;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.definition.FlowDefinition;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.core.model.definition.ParamValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowValidator {
    private final ApplicationContext applicationContext;

    public void validNodes(FlowDefinition flowDefinition) {
        HashSet<String> nodeIdSet = new HashSet<>();
        for (FlowNode node : flowDefinition.getNodes()) {
            // 检查 nodeId 是否重复
            if (nodeIdSet.contains(node.getNodeId())) {
                throw new DuplicateNodeId(flowDefinition.getName(), node.getNodeId());
            }
            // 检查是否有实现
            this.getNodeByName(node.getName());
            // 将 ID 放入 set 中
            nodeIdSet.add(node.getNodeId());
        }
        this.topologicalSort(flowDefinition);
    }

    /**
     * 拓扑排序（基于节点内的nextNodeIds）
     */
    public List<FlowNode> topologicalSort(FlowDefinition flowDefinition) {
        Map<String, FlowNode> nodeMap = flowDefinition.getNodes().stream()
                .collect(Collectors.toMap(FlowNode::getNodeId, n -> n));

        // 1. 计算入度
        Map<String, Integer> indegree = new HashMap<>();
        for (FlowNode node : flowDefinition.getNodes()) {
            indegree.putIfAbsent(node.getNodeId(), 0);
            for (String nextId : node.getNextNodeIds()) {
                indegree.merge(nextId, 1, Integer::sum);
            }
        }
        // 2. 找到入度为0的起始节点
        Queue<FlowNode> queue = new LinkedList<>();
        for (FlowNode node : flowDefinition.getNodes()) {
            if (indegree.get(node.getNodeId()) == 0) {
                queue.offer(node);
            }
        }
        // 3. 拓扑排序
        List<FlowNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            FlowNode current = queue.poll();
            sorted.add(current);

            for (String nextId : current.getNextNodeIds()) {
                FlowNode nextNode = nodeMap.get(nextId);
                int newIndegree = indegree.get(nextId) - 1;
                indegree.put(nextId, newIndegree);

                if (newIndegree == 0) {
                    queue.offer(nextNode);
                }
            }
        }
        // 4. 检查环
        if (sorted.size() != flowDefinition.getNodes().size()) {
            throw new CyclicDependency(flowDefinition.getName());
        }

        return sorted;
    }

    public Node getAnnotationByNodeName(String nodeName) {
        BaseNode node = this.getNodeByName(nodeName);
        return AnnotationUtils.findAnnotation(node.getClass(), Node.class);
    }

    public BaseNode getNodeByName(String nodeName) {
        Map<String, BaseNode> nodeList = this.applicationContext.getBeansOfType(BaseNode.class);
        for (BaseNode node : nodeList.values()) {
            Node annotation = AnnotationUtils.findAnnotation(node.getClass(), Node.class);
            if (ObjectUtils.isEmpty(annotation)) {
                log.warn("node:{} 继承了 BaseNode, 但是没有 Node 注解", node.getClass());
                continue;
            }
            if (annotation.name().equals(nodeName)) {
                return node;
            }
        }
        throw new NodeImplementNotFound("找不到 %s 节点实现".formatted(nodeName));
    }

    public List<Node> getAllNodeAnnotation() {
        Map<String, BaseNode> nodeList = this.applicationContext.getBeansOfType(BaseNode.class);
        ArrayList<Node> result = new ArrayList<>();
        nodeList.forEach((k, v) -> {
            Node annotation = AnnotationUtils.findAnnotation(v.getClass(), Node.class);
            if (ObjectUtils.isNotEmpty(annotation)) {
                result.add(annotation);
            }
        });
        return result;
    }
}
