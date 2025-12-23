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
 * 节点执行日志表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "node_execution", autoResultMap = true)
public class NodeExecutionEntity extends BaseEntity {
    /**
     * 节点执行日志ID
     */
    @TableId(value = "node_execution_id", type = IdType.AUTO)
    private Long nodeExecutionId;

    /**
     * 执行生成的 uuid v4
     */
    @TableField("node_execution_uuid_v4")
    private String nodeExecutionUuidV4;

    /**
     * 流程定义ID
     */
    @TableField("flow_definition_id")
    private Long flowDefinitionId;

    /**
     * 流程执行ID
     */
    @TableField("flow_execution_id")
    private Long flowExecutionId;

    /**
     * 节点名称
     */
    @TableField("node_id")
    private String nodeId;

    /**
     * 执行状态（pending, running, success, failed, cancelled）
     */
    @TableField("execution_status")
    private String executionStatus;

    /**
     * 输入参数JSON
     */
    @TableField(value = "input_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputData;

    /**
     * 输出参数JSON
     */
    @TableField(value = "output_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputData;

    /**
     * 执行日志
     */
    @TableField("node_execution_log")
    private String nodeExecutionLog;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private Timestamp startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private Timestamp endTime;
}
