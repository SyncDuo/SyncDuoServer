package com.syncduo.server.core.model.definition;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class FlowNode {
    private String nodeId; // 唯一ID, dag 内不能重复

    private String name; // 用于匹配Node注解

    private Map<String, ParamValue> inputParams; // key 用于匹配 node 注解中的 inputParams

    private List<String> nextNodeIds;
}
