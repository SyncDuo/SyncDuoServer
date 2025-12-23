package com.syncduo.server.workflow.model.db;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.syncduo.server.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.sql.Timestamp;
import java.util.Map;

/**
 * 流程执行记录表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "flow_execution", autoResultMap = true)
public class FlowExecutionEntity extends BaseEntity {

    /**
     * 流程执行记录ID
     */
    @TableId(value = "flow_execution_id", type = IdType.AUTO)
    private Long flowExecutionId;

    /**
     * 执行生成的 uuid v4
     */
    @TableField("flow_execution_uuid_v4")
    private String flowExecutionUuidV4;

    /**
     * 流程定义ID
     */
    @TableField("flow_definition_id")
    private Long flowDefinitionId;

    /**
     * 执行状态（running, success, failed）
     */
    @TableField("execution_status")
    private String executionStatus;

    /**
     * 执行结果JSON
     */
    @TableField(value = "context_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contextData;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 开始时间
     */
    @TableField(value = "start_time", fill = FieldFill.INSERT)
    private Timestamp startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private Timestamp endTime;
}
