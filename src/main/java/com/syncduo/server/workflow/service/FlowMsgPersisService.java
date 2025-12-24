package com.syncduo.server.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.exception.ResourceNotFoundException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.engine.FlowValidator;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.core.channel.ChannelService;
import com.syncduo.server.workflow.core.channel.Message;
import com.syncduo.server.workflow.mapper.FlowDefinitionMapper;
import com.syncduo.server.workflow.mapper.FlowExecutionMapper;
import com.syncduo.server.workflow.mapper.NodeExecutionMapper;
import com.syncduo.server.workflow.model.api.info.FlowInfoDTO;
import com.syncduo.server.workflow.model.db.FlowDefinitionEntity;
import com.syncduo.server.workflow.model.db.FlowExecutionEntity;
import com.syncduo.server.workflow.model.db.NodeExecutionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowMsgPersisService implements SmartLifecycle {
    private final Thread msgHandleThread = new Thread(this::persisMsg, "FlowMsg-Persis-Thread");
    private final ChannelService channel;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final FlowExecutionMapper flowExecutionMapper;
    private final NodeExecutionMapper nodeExecutionMapper;
    private final FlowValidator flowValidator;

    public List<FlowInfoDTO> getAllFlowInfo() {
        LambdaQueryWrapper<FlowDefinitionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FlowDefinitionEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        List<FlowDefinitionEntity> dbResult = this.flowDefinitionMapper.selectList(queryWrapper);
        List<FlowInfoDTO> result = new ArrayList<>();
        for (FlowDefinitionEntity entity : dbResult) {
            FlowInfoDTO flowInfoDTO = new FlowInfoDTO()
                    .setFlowDefinitionId(entity.getFlowDefinitionId())
                    .setFlowName(entity.getName())
                    .setCronConfig(entity.getCronConfig())
                    .setEnabled(entity.getEnabled());
            FlowExecutionEntity lastExecution = this.getLastExecution(entity);
            if (ObjectUtils.isEmpty(lastExecution)) {
                result.add(flowInfoDTO);
                continue;
            }
            flowInfoDTO.setLastExecutionExecStatus(lastExecution.getExecutionStatus())
                    .setLastExecutionDuration(this.calLastExecutionDuration(lastExecution));
            result.add(flowInfoDTO);
        }
        return result;
    }

    private Long calLastExecutionDuration(FlowExecutionEntity flowExecution) {
        Instant startTime = flowExecution.getStartTime().toInstant();
        Timestamp endTime = flowExecution.getEndTime();
        return ObjectUtils.isEmpty(endTime) ?
                Duration.between(startTime, Instant.now()).getSeconds() :
                Duration.between(startTime, endTime.toInstant()).getSeconds();
    }

    private FlowExecutionEntity getLastExecution(FlowDefinitionEntity flowDefinitionEntity) {
        LambdaQueryWrapper<FlowExecutionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FlowExecutionEntity::getFlowDefinitionId, flowDefinitionEntity.getFlowDefinitionId());
        queryWrapper.orderByDesc(FlowExecutionEntity::getStartTime);
        List<FlowExecutionEntity> dbResult = this.flowExecutionMapper.selectList(queryWrapper);
        return CollectionUtils.isEmpty(dbResult) ? null : dbResult.get(0);
    }

    private void persisMsg() {
        while (!Thread.currentThread().isInterrupted()) {
            Message msg = null;
            try {
                msg = this.channel.take();
                if (msg.type().equals("FLOW")) {
                    this.handleFlowMsg(msg);
                } else if (msg.type().equals("NODE")) {
                    this.handleNodeMsg(msg);
                } else {
                    throw new ValidationException("未识别的 msg type:%s".formatted(msg.type()));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("msg:{} 处理失败", msg, e);
            }
        }
    }

    private void handleNodeMsg(Message msg) {
        NodeExecutionEntity entity = createNodeExecutionFromMsg(msg);
        switch (msg.execStatus()) {
            case RUNNING -> {
                entity.setStartTime(Timestamp.from(Instant.now()));
                isDbOperationSuccess(this.nodeExecutionMapper.insert(entity));
            }
            case SUCCESS, FAILED -> {
                // 查找数据库
                NodeExecutionEntity dbResult = this.getNodeExecutionByUuidV4(msg.nodeExecutionUuidV4());
                if (ObjectUtils.isEmpty(dbResult)) {
                    throw new ResourceNotFoundException("找不到 node execution." +
                            "UUID 是 %s".formatted(msg.nodeExecutionUuidV4()));
                }
                // entity 设置数据库 ID, output data 和 end time
                entity.setNodeExecutionId(dbResult.getNodeExecutionId())
                        .setOutputData(this.getNodeData(msg.node(), msg.data(), false))
                        .setEndTime(Timestamp.from(Instant.now()));
                isDbOperationSuccess(this.nodeExecutionMapper.updateById(entity));
            }
        }
    }

    private NodeExecutionEntity createNodeExecutionFromMsg(Message msg) {
        FlowExecutionEntity flowExecution = this.getFlowExecutionByUuidV4(msg.flowExecutionUuidV4());
        if (ObjectUtils.isEmpty(flowExecution)) {
            throw new ResourceNotFoundException("找不到 flow execution. " +
                    "UUID 是 %s".formatted(msg.flowExecutionUuidV4()));
        }
        return new NodeExecutionEntity()
                .setNodeExecutionUuidV4(msg.nodeExecutionUuidV4().toString())
                .setFlowDefinitionId(flowExecution.getFlowDefinitionId())
                .setFlowExecutionId(flowExecution.getFlowExecutionId())
                .setNodeId(msg.node().getNodeId())
                .setNodeName(msg.node().getName())
                .setExecutionStatus(msg.execStatus().name())
                .setInputData(this.getNodeData(msg.node(), msg.data(), true))
                .setNodeExecutionLog(msg.errorMessage());
    }

    private NodeExecutionEntity getNodeExecutionByUuidV4(UUID uuidV4) {
        LambdaQueryWrapper<NodeExecutionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NodeExecutionEntity::getNodeExecutionUuidV4, uuidV4.toString());
        return this.nodeExecutionMapper.selectOne(queryWrapper);
    }

    private FlowExecutionEntity getFlowExecutionByUuidV4(UUID uuidV4) {
        LambdaQueryWrapper<FlowExecutionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FlowExecutionEntity::getFlowExecutionUuidV4, uuidV4.toString());
        return this.flowExecutionMapper.selectOne(queryWrapper);
    }

    private void handleFlowMsg(Message msg) {
        FlowExecutionEntity entity = this.createFlowExecutionFromMsg(msg);
        switch (msg.execStatus()) {
            case RUNNING -> {
                entity.setStartTime(Timestamp.from(Instant.now()));
                isDbOperationSuccess(this.flowExecutionMapper.insert(entity));
            }
            case SUCCESS, FAILED -> {
                // 查找数据库
                LambdaQueryWrapper<FlowExecutionEntity> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(FlowExecutionEntity::getFlowExecutionUuidV4, entity.getFlowExecutionUuidV4());
                FlowExecutionEntity dbResult = this.flowExecutionMapper.selectOne(queryWrapper);
                if (ObjectUtils.isEmpty(dbResult)) {
                    throw new ResourceNotFoundException(("找不到 flow execution. " +
                            "uuid 是 %s").formatted(entity.getFlowExecutionUuidV4()));
                }
                // entity 设置数据库 id 和 结束时间
                entity.setFlowExecutionId(dbResult.getFlowExecutionId())
                        .setEndTime(Timestamp.from(Instant.now()));
                // 更新数据库
                isDbOperationSuccess(this.flowExecutionMapper.updateById(entity));
            }
        }
    }

    private FlowExecutionEntity createFlowExecutionFromMsg(Message msg) {
        return new FlowExecutionEntity()
                .setFlowExecutionUuidV4(msg.flowExecutionUuidV4().toString())
                .setFlowDefinitionId(msg.registerId())
                .setExecutionStatus(msg.execStatus().name())
                .setContextData(msg.data())
                .setErrorMessage(msg.errorMessage());
    }

    private void isDbOperationSuccess(int count) {
        if (count != 1) {
            throw new DbException("db 操作失败, count = %s".formatted(count));
        }
    }

    private Map<String, Object> getNodeData(FlowNode node, Map<String, Object> contextData, boolean input) {
        Node annotation = this.flowValidator.getAnnotationByNodeName(node.getName());
        String[] paramKeys = input ? annotation.inputParams() : annotation.outputParams();
        HashMap<String, Object> result = new HashMap<>();
        for (String paramKey : paramKeys) {
            result.put(paramKey, contextData.get(paramKey));
        }
        return result;
    }

    @Override
    public void start() {
        msgHandleThread.start();
    }

    @Override
    public void stop() {
        msgHandleThread.interrupt();
    }

    @Override
    public boolean isRunning() {
        return msgHandleThread.isAlive();
    }
}
