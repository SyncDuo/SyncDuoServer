package com.syncduo.server.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.workflow.core.engine.FlowEngine;
import com.syncduo.server.workflow.mapper.FlowDefinitionMapper;
import com.syncduo.server.workflow.model.db.FlowDefinitionEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowScheduler implements SmartLifecycle {
    private final FlowEngine flowEngine;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final TaskScheduler systemManagementTaskScheduler;
    private final Map<Long, FlowScheduleInfo> flowScheduleMap = new ConcurrentHashMap<>();

    public void schedule(FlowDefinitionEntity entity) {
        // 幂等
        Long flowDefinitionId = entity.getFlowDefinitionId();
        if (flowScheduleMap.containsKey(flowDefinitionId)) {
            return;
        }
        // 开始调度
        ScheduledFuture<?> scheduledFuture = this.systemManagementTaskScheduler.schedule(
                () -> this.checkAndExecution(entity),
                new CronTrigger(entity.getCronConfig())
        );
        // 放入 map
        flowScheduleMap.put(flowDefinitionId, new FlowScheduleInfo(scheduledFuture));
    }

    private void checkAndExecution(FlowDefinitionEntity entity) {
        Long flowDefinitionId = entity.getFlowDefinitionId();
        FlowScheduleInfo flowScheduleInfo = flowScheduleMap.get(flowDefinitionId);
        if (flowScheduleInfo.isExecutionIdle()) {
            // 开始执行
            flowScheduleInfo.execution = this.flowEngine.execute(flowDefinitionId, entity.getDefinition());
        } else {
            // 跳过本次执行
            log.warn("跳过 flow 执行. flow definition id: {}", flowDefinitionId);
        }
    }

    public FlowDefinitionEntity stopSchedule(Long flowDefinitionId) {
        FlowScheduleInfo flowScheduleInfo = this.flowScheduleMap.get(flowDefinitionId);
        if (ObjectUtils.isNotEmpty(flowScheduleInfo)) {
            flowScheduleInfo.stop();
            this.flowScheduleMap.remove(flowDefinitionId);
        }
        FlowDefinitionEntity dbResult = this.flowDefinitionMapper.selectById(flowDefinitionId);
        if (ObjectUtils.isNotEmpty(dbResult)) {
            dbResult.setEnabled(0);
            int count = this.flowDefinitionMapper.updateById(dbResult);
            if (count != 1) {
                throw new DbException("db 操作失败, count = %s".formatted(count));
            }
            return dbResult;
        }
        return null;
    }

    public void deleteFlow(Long flowDefinitionId) {
        FlowDefinitionEntity dbResult = this.stopSchedule(flowDefinitionId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.setRecordDeleted(DeletedEnum.DELETED.getCode());
        int count = this.flowDefinitionMapper.updateById(dbResult);
        if (count != 1) {
            throw new DbException("db 操作失败, count = %s".formatted(count));
        }
    }

    public void enableSchedule(Long flowDefinitionId) {
        FlowDefinitionEntity dbResult = this.flowDefinitionMapper.selectById(flowDefinitionId);
        if (ObjectUtils.isEmpty(dbResult)) {
            return;
        }
        if (dbResult.getRecordDeleted().equals(DeletedEnum.DELETED.getCode())) {
            return;
        }
        if (dbResult.getEnabled().equals(0)) {
            dbResult.setEnabled(1);
            this.flowDefinitionMapper.updateById(dbResult);
        }
        this.schedule(dbResult);
    }

    @Override
    public void start() {
        //  获取全部 enable, 没有删除的 flow
        LambdaQueryWrapper<FlowDefinitionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FlowDefinitionEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        queryWrapper.eq(FlowDefinitionEntity::getEnabled, 1);
        List<FlowDefinitionEntity> dbResult = this.flowDefinitionMapper.selectList(queryWrapper);
        for (FlowDefinitionEntity entity : dbResult) {
            this.schedule(entity);
        }
    }

    @Override
    public void stop() {
        // 先暂停 scheduler, 再暂停 execution
        this.flowScheduleMap.forEach((k, v) -> this.stopSchedule(k));
        this.flowScheduleMap.clear();
    }

    @Override
    public boolean isRunning() {
        return MapUtils.isEmpty(flowScheduleMap);
    }

    @RequiredArgsConstructor
    @Data
    protected static class FlowScheduleInfo {
        private final ScheduledFuture<?> cronScheduler;
        private Future<?> execution;

        public boolean isExecutionIdle() {
            return ObjectUtils.isEmpty(execution) || execution.isDone();
        }

        public void stop() {
            cronScheduler.cancel(true);
            if (ObjectUtils.isNotEmpty(execution)) {
                execution.cancel(true);
            }
        }
    }
}
