package com.syncduo.server.service.bussiness;

import com.syncduo.server.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;


@Slf4j
@Service
public class DebounceService {

    @Qualifier("generalTaskScheduler")
    private final TaskScheduler generalTaskScheduler;

    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    private final Map<String, ScheduledFuture<?>> cancelTaskMap = new ConcurrentHashMap<>();

    @Autowired
    public DebounceService(TaskScheduler generalTaskScheduler) {
        this.generalTaskScheduler = generalTaskScheduler;
    }

    public void schedule(Runnable task, long delaySec) {
        this.generalTaskScheduler.schedule(task, Instant.now().plusSeconds(delaySec));
    }

    public void scheduleAndCancelAfter(
            String key,
            Supplier<Boolean> task,
            long periodSec,
            long cancelAfterSec,
            CompletableFuture<?> future) {
        // 启动周期任务
        ScheduledFuture<?> scheduledTask = this.generalTaskScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        Boolean stopEarly = task.get();
                        if (stopEarly) {
                            this.earlyCancel(key);
                        }
                    } catch (Exception e) {
                        future.obtrudeException(new BusinessException(
                                "scheduleAndCancelAfter failed. key is %s".formatted(key),
                                e));
                        // task 发生异常, 则终止执行
                        this.earlyCancel(key);
                    }
                },
                Duration.ofSeconds(periodSec)
        );
        this.taskMap.put(key, scheduledTask);
        // 启动一个一次性的任务
        ScheduledFuture<?> cancelTask = this.generalTaskScheduler.schedule(() -> {
            // 取消周期任务
            this.cancel(key);
            // future 返回超时异常
            future.obtrudeException(new BusinessException(
                    "scheduleAndCancelAfter timeout. key is %s".formatted(key)));
        }, Instant.now().plusSeconds(cancelAfterSec));
        this.cancelTaskMap.put(key, cancelTask);
    }

    public void debounce(String key, Runnable task, long delaySec) {
        // 有重复的未执行的task则取消
        this.cancel(key);
        // 规划新的task
        ScheduledFuture<?> newTask = this.generalTaskScheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("execute debounce task(key:{}, task:{} failed", key, task, e);
                // task 发生异常, 将 task 从 map 中移除, 也就是终止执行
                this.cancel(key);
            }
        }, Instant.now().plusSeconds(delaySec));
        // 新的task放进map中
        this.taskMap.put(key, newTask);
    }

    // cancel schedule task and cancel task
    public void earlyCancel(String key) {
        this.cancel(key);
        ScheduledFuture<?> cancelTask = this.cancelTaskMap.get(key);
        if (ObjectUtils.isNotEmpty(cancelTask)) {
            cancelTask.cancel(false);
            cancelTaskMap.remove(key);
        }
    }

    // only cancel schedule task
    public void cancel(String key) {
        ScheduledFuture<?> existingTask = taskMap.get(key);
        if (ObjectUtils.isNotEmpty(existingTask)) {
            existingTask.cancel(false);
            taskMap.remove(key);
        }
    }

    // 创建模块专用的Debounce服务
    public ModuleDebounceService forModule(String moduleName) {
        return new ModuleDebounceService(this, moduleName);
    }

    // 模块专用的Debounce包装器
    public static class ModuleDebounceService {

        private final DebounceService debounceService;

        private final String modulePrefix;

        public ModuleDebounceService(DebounceService debounceService, String moduleName) {
            this.debounceService = debounceService;
            this.modulePrefix = moduleName + "::";
        }

        public void debounce(String key, Runnable task, long delaySec) {
            String fullKey = modulePrefix + key;
            debounceService.debounce(fullKey, task, delaySec);
        }

        public void schedule(Runnable task, long delaySec) {
            debounceService.schedule(task, delaySec);
        }

        public void cancelAfter(
                String key,
                Supplier<Boolean> task,
                long periodSec,
                long cancelAfterSec,
                CompletableFuture<?> future) {
            String fullKey = modulePrefix + key;
            debounceService.scheduleAndCancelAfter(fullKey, task, periodSec, cancelAfterSec, future);
        }

        public void cancel(String key) {
            String fullKey = modulePrefix + key;
            debounceService.cancel(fullKey);
        }

        public void earlyCancel(String key) {
            String fullKey = modulePrefix + key;
            debounceService.earlyCancel(fullKey);
        }
    }
}
