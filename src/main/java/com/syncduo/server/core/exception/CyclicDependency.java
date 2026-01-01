package com.syncduo.server.core.exception;

public class CyclicDependency extends BaseException {
    public CyclicDependency(String flowName) {
        super("FlowDefinition:%s 中存在环".formatted(flowName));
    }
}
