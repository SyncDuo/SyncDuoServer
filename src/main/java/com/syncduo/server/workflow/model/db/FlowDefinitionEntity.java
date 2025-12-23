package com.syncduo.server.workflow.model.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.syncduo.server.model.entity.BaseEntity;
import com.syncduo.server.workflow.core.model.definition.FlowDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 流程定义表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("flow_definition")
public class FlowDefinitionEntity extends BaseEntity {

    /**
     * 流程定义ID
     */
    @TableId(value = "flow_definition_id", type = IdType.AUTO)
    private Long flowDefinitionId;

    /**
     * 流程名称
     */
    @TableField("name")
    private String name;

    /**
     * 流程描述
     */
    @TableField("description")
    private String description;

    /**
     * 完整的流程定义JSON
     * 使用JacksonTypeHandler自动处理JSON类型
     */
    @TableField(value = "definition", typeHandler = JacksonTypeHandler.class)
    private FlowDefinition definition;

    /**
     * Cron 表达式
     * 使用JacksonTypeHandler自动处理JSON类型
     */
    @TableField(value = "cron_config")
    private String cronConfig;

    /**
     * 是否启用（1:启用, 0:禁用）
     */
    @TableField("enabled")
    private Integer enabled;
}
