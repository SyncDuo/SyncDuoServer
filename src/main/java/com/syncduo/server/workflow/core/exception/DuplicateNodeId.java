package com.syncduo.server.workflow.core.exception;

public class DuplicateNodeId extends BaseException {
    public DuplicateNodeId(String flowName, String nodeId) {
        super("flow %s 含有重复的 nodeId %s".formatted(flowName, nodeId));
    }
}
