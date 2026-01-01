package com.syncduo.server.core.exception;

public class NodeImplementNotFound extends BaseException {
    public NodeImplementNotFound(String nodeName) {
        super("节点 %s 找不到实现".formatted(nodeName));
    }
}
