package com.syncduo.server.workflow.core.model.execution;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@RequiredArgsConstructor
public class FlowContext {
    private final Long registerId;
    private final String name;
    private final UUID flowExecutionUuidV4 = UUID.randomUUID();
    private final Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public void putAll(NodeResult nodeResult) {
        this.data.putAll(nodeResult.getReturnVal());
    }

    public Object get(String key) {
        return this.data.get(key);
    }

    public Map<String, Object> getAllData() {
        return new HashMap<>(data);
    }
}
