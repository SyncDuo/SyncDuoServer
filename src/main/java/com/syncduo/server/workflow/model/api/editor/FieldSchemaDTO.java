package com.syncduo.server.workflow.model.api.editor;

import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class FieldSchemaDTO {
    private String nodeId;
    private String nodeName;
    private final Map<String, FieldSchema> fieldSchemaMap = new HashMap<>();

    public FieldSchemaDTO(FlowNode flowNode) {
        this.nodeId = flowNode.getNodeId();
        this.nodeName = flowNode.getName();
    }

    public void addFieldSchema(String paramName, String sourceType, FieldRegistry.Definition definition) {
        this.fieldSchemaMap.put(paramName, new FieldSchema(
                sourceType,
                definition.typeReference().getType().getTypeName(),
                definition.description(),
                definition.group(),
                null
        ));
    }

    public void addFieldSchema(String paramName, String sourceType, FieldRegistry.Definition definition, Object value) {
        this.fieldSchemaMap.put(paramName, new FieldSchema(
                sourceType,
                definition.typeReference().getType().getTypeName(),
                definition.description(),
                definition.group(),
                value
        ));
    }

    public FieldSchema getFieldSchema(String paramKey) {
        return this.fieldSchemaMap.get(paramKey);
    }

    public record FieldSchema(
            String sourceType,
            String typeReference,
            String description,
            String group,
            Object value) {}
}
