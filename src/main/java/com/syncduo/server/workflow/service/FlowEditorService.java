package com.syncduo.server.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.engine.FlowValidator;
import com.syncduo.server.workflow.core.enums.ParamSourceType;
import com.syncduo.server.workflow.core.model.definition.FlowDefinition;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.core.model.definition.ParamValue;
import com.syncduo.server.workflow.mapper.FlowDefinitionMapper;
import com.syncduo.server.workflow.model.api.editor.CreateFlowRequest;
import com.syncduo.server.workflow.model.api.editor.FieldSchemaDTO;
import com.syncduo.server.workflow.model.db.FlowDefinitionEntity;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowEditorService {
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final FlowValidator flowValidator;

    public FlowDefinitionEntity createFlow(CreateFlowRequest createFlowRequest) {
        FlowDefinitionEntity dbResult = this.getEntityByName(createFlowRequest.flowName());
        if (ObjectUtils.isNotEmpty(dbResult)) {
            throw new BusinessException("重复 flow name %s".formatted(createFlowRequest.flowName()));
        }
        try {
            CronExpression.parse(createFlowRequest.cron());
        } catch (Exception e) {
            throw new ValidationException("cron 表达式 %s 错误".formatted(createFlowRequest.cron()), e);
        }
        FlowDefinitionEntity entity = this.createEntityFromRequest(createFlowRequest);
        int count = this.flowDefinitionMapper.insert(entity);
        if (count != 1) {
            throw new BusinessException("DB 操作失败, count %s != 1".formatted(count));
        }
        return entity;
    }

    private FlowDefinitionEntity createEntityFromRequest(CreateFlowRequest createFlowRequest) {
        String flowName = createFlowRequest.flowName();
        FlowDefinition flowDefinition = new FlowDefinition(flowName);
        flowDefinition.setNodes(createFlowRequest.nodeList());
        for (FieldSchemaDTO fieldSchemaDTO : createFlowRequest.fieldSchemaDTOList()) {
            HashMap<String, ParamValue> inputParams = new HashMap<>();
            fieldSchemaDTO.getFieldSchemaMap().forEach((k, v) ->
                    inputParams.put(k, new ParamValue(v.value(), ParamSourceType.valueOf(v.sourceType()))));
            for (FlowNode flowNode : flowDefinition.getNodes()) {
                if (flowNode.getNodeId().equals(fieldSchemaDTO.getNodeId())) {
                    flowNode.setInputParams(inputParams);
                }
            }
        }
        return new FlowDefinitionEntity()
                .setName(flowName)
                .setDescription(createFlowRequest.description())
                .setDefinition(flowDefinition)
                .setCronConfig(createFlowRequest.cron())
                .setEnabled(1);
    }

    public List<FieldSchemaDTO> getFieldSchemaDTOFromNodes(List<FlowNode> nodeList) {
        List<FieldSchemaDTO> result = new ArrayList<>();
        // 拓扑排序
        List<FlowNode> sortedNodes = this.flowValidator.topologicalSort(new FlowDefinition("tmp", nodeList));
        // 按顺序遍历, 判断 node input param 是否在前置 node 的 output param 中
        // 是则 param source type = node_output
        Set<String> outputParamKeys = new HashSet<>();
        for (FlowNode sortedNode : sortedNodes) {
            FieldSchemaDTO fieldSchemaDTO = new FieldSchemaDTO(sortedNode);
            Node annotation = this.flowValidator.getAnnotationByNodeName(sortedNode.getName());
            String[] inputParamKeys = annotation.inputParams();
            for (String inputParamKey : inputParamKeys) {
                if (outputParamKeys.contains(inputParamKey)) {
                    fieldSchemaDTO.addFieldSchema(
                            inputParamKey,
                            ParamSourceType.NODE_OUTPUT.name(),
                            FieldRegistry.getMeta(inputParamKey)
                    );
                } else {
                    fieldSchemaDTO.addFieldSchema(
                            inputParamKey,
                            ParamSourceType.MANUAL.name(),
                            FieldRegistry.getMeta(inputParamKey)
                    );
                }
            }
            result.add(fieldSchemaDTO);
            outputParamKeys.addAll(Arrays.stream(annotation.outputParams()).toList());
        }
        return result;
    }

    public void validNodesOrchestrate(List<FlowNode> nodeList) {
        // 校验 nodeId 是否重复, node 是否有实现, node 是否有环
        try {
            this.flowValidator.validNodes(new FlowDefinition("tmp", nodeList));
        } catch (Exception e) {
            throw new ValidationException("node 编排错误", e);
        }
    }

    public void validParams(List<FieldSchemaDTO> fieldSchemaDTOList) {
        // 校验 param value 是否都符合 FieldRegistry
        for (FieldSchemaDTO fieldSchemaDTO : fieldSchemaDTOList) {
            // 检查是否有遗漏的 input param 没有填写, 是否 source type 没有指定
            this.isInputParamMissing(fieldSchemaDTO);
            // 检查 param value 是否符合 definition
            this.isInputValueValid(fieldSchemaDTO);
        }
    }

    private void isInputValueValid(FieldSchemaDTO fieldSchemaDTO) {
        fieldSchemaDTO.getFieldSchemaMap().forEach((k, v) -> {
            if (ParamSourceType.NODE_OUTPUT.name().equals(v.sourceType())) {
                return;
            }
            FieldRegistry.Definition definition = FieldRegistry.getMeta(k);
            try {
                boolean validate = JsonUtil.validateType(definition.typeReference(), v.value());
                if (!validate) {
                    throw new ValidationException("isInputValueValid 失败");
                }
            } catch (Exception e) {
                throw new ValidationException("节点%s 的参数 %s 的值(%s)的类型(%s)不符合定义(%s)"
                        .formatted(
                                fieldSchemaDTO.getNodeId(),
                                k,
                                v.value(),
                                v.value() == null ? "Void" : v.value().getClass(),
                                definition.typeReference().getType().getTypeName()));
            }
        });
    }

    private void isInputParamMissing(FieldSchemaDTO fieldSchemaDTO) {
        Node nodeMeta = this.flowValidator.getAnnotationByNodeName(fieldSchemaDTO.getNodeName());
        for (String paramKey : nodeMeta.inputParams()) {
            FieldSchemaDTO.FieldSchema fieldSchema = fieldSchemaDTO.getFieldSchema(paramKey);
            if (ObjectUtils.isEmpty(fieldSchema)) {
                throw new ValidationException("nodeId:%s(nodeName:%s) 缺少 param %s."
                        .formatted(fieldSchemaDTO.getNodeId(), fieldSchemaDTO.getNodeName(), paramKey));
            }
            if (ObjectUtils.isEmpty(fieldSchema.sourceType())) {
                throw new ValidationException("nodeId:%s(nodeName:%s)的 param %s 缺少 source type"
                        .formatted(fieldSchemaDTO.getNodeId(), fieldSchemaDTO.getNodeName(), paramKey));
            }
        }
    }

    private FlowDefinitionEntity getEntityByName(String name) {
        LambdaQueryWrapper<FlowDefinitionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FlowDefinitionEntity::getName, name);
        queryWrapper.eq(FlowDefinitionEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        return this.flowDefinitionMapper.selectOne(queryWrapper);
    }

    public List<Node> getAllNode() {
        return this.flowValidator.getAllNodeAnnotation();
    }
}
