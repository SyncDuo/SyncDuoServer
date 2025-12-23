package com.syncduo.server.workflow.core.model.execution;

import com.syncduo.server.workflow.core.enums.ExecStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.*;

@Data
@RequiredArgsConstructor
@Accessors(chain = true)
public class NodeResult {
    private final ExecStatus execStatus;
    private final Map<String, Object> returnVal;
    private String error = "";

    public static NodeResult success(Map<String, Object> result) {
        return new NodeResult(ExecStatus.SUCCESS, new HashMap<>(result));
    }

    public static NodeResult success() {
        return new NodeResult(ExecStatus.SUCCESS, new HashMap<>());
    }

    public static NodeResult failed(String error) {
        return new NodeResult(ExecStatus.FAILED, new HashMap<>()).setError(error);
    }
}
