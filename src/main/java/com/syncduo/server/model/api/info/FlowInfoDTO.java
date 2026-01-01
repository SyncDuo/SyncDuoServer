package com.syncduo.server.model.api.info;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class FlowInfoDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowDefinitionId;

    private String flowName;

    private String cronConfig;

    private String lastExecutionExecStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastExecutionDuration; // 秒

    private Integer enabled;
}
