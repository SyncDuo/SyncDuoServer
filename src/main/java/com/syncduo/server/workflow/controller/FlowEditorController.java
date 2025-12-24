package com.syncduo.server.workflow.controller;

import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.model.api.editor.CreateFlowRequest;
import com.syncduo.server.workflow.model.api.editor.FieldSchemaDTO;
import com.syncduo.server.workflow.model.api.editor.NodeAnnotationInfo;
import com.syncduo.server.workflow.model.api.global.FlowResponse;
import com.syncduo.server.workflow.model.db.FlowDefinitionEntity;
import com.syncduo.server.workflow.service.FlowEditorService;
import com.syncduo.server.workflow.service.FlowScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@Slf4j
@CrossOrigin(originPatterns = "*")
@RequestMapping("/flow-editor")
@RequiredArgsConstructor
public class FlowEditorController {
    private final FlowEditorService flowEditorService;
    private final FlowScheduler flowScheduler;

    @PostMapping("/valid-nodes")
    public FlowResponse<Void> validNodes(@RequestBody List<FlowNode> nodeList) {
        // 校验非空
        this.validFlowNodes(nodeList);
        this.flowEditorService.validNodesOrchestrate(nodeList);
        return FlowResponse.success();
    }

    @PostMapping("/get-field-schema")
    public FlowResponse<List<FieldSchemaDTO>> getFieldSchema(@RequestBody List<FlowNode> nodeList) {
        // 校验非空
        this.validFlowNodes(nodeList);
        List<FieldSchemaDTO> result = this.flowEditorService.getFieldSchemaDTOFromNodes(nodeList);
        return FlowResponse.success(result);
    }

    @PostMapping("/valid-field-schema")
    public FlowResponse<Void> validFieldSchema(@RequestBody List<FieldSchemaDTO> fieldSchemaDTOList) {
        this.validFieldSchemaDTO(fieldSchemaDTOList);
        this.flowEditorService.validParams(fieldSchemaDTOList);
        return FlowResponse.success();
    }

    @PostMapping("/create-flow")
    public FlowResponse<FlowDefinitionEntity> createFlow(@RequestBody CreateFlowRequest createFlowRequest)  {
        if (ObjectUtils.isEmpty(createFlowRequest)) {
            throw new ValidationException("createFlowRequest 不能为空");
        }
        if (StringUtils.isAnyBlank(
                createFlowRequest.flowName(),
                createFlowRequest.cron(),
                createFlowRequest.description())) {
            throw new ValidationException("flowName, cron, description 不能为空");
        }
        this.validFlowNodes(createFlowRequest.nodeList());
        this.validFieldSchema(createFlowRequest.fieldSchemaDTOList());
        FlowDefinitionEntity entity = this.flowEditorService.createFlow(createFlowRequest);
        // 开始调度
        this.flowScheduler.schedule(entity);
        return FlowResponse.success(entity);
    }

    @GetMapping("/get-all-nodes")
    public FlowResponse<List<NodeAnnotationInfo>> getAllNodes() {
        List<Node> result = this.flowEditorService.getAllNode();
        if (CollectionUtils.isEmpty(result)) {
            return FlowResponse.success(Collections.emptyList());
        } else {
            return FlowResponse.success(result.stream()
                    .map(node -> new NodeAnnotationInfo(node.name(), node.description(), node.group()))
                    .toList()
            );
        }
    }

    private void validFlowNodes(List<FlowNode> nodeList) {
        // 校验非空
        if (CollectionUtils.isEmpty(nodeList)) {
            throw new ValidationException("nodeList 不能为空");
        }
        for (FlowNode flowNode : nodeList) {
            if (StringUtils.isAnyBlank(flowNode.getNodeId(), flowNode.getName())) {
                throw new ValidationException("nodeId, name 不能为空");
            }
        }
    }

    private void validFieldSchemaDTO(List<FieldSchemaDTO> fieldSchemaDTOList) {
        if (CollectionUtils.isEmpty(fieldSchemaDTOList)) {
            throw new ValidationException("fieldSchemaDTOList 不能为空");
        }
        for (FieldSchemaDTO fieldSchemaDTO : fieldSchemaDTOList) {
            if (StringUtils.isAnyBlank(fieldSchemaDTO.getNodeId(), fieldSchemaDTO.getNodeName())) {
                throw new ValidationException("nodeId, nodeName 不能为空");
            }
        }
    }
}
