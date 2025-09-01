package com.syncduo.server.service.bussiness;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;


@Slf4j
@Service
public class DebounceService {

    @Qualifier("generalTaskScheduler")
    private final TaskScheduler generalTaskScheduler;

    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    @Autowired
    public DebounceService(TaskScheduler generalTaskScheduler) {
        this.generalTaskScheduler = generalTaskScheduler;
    }

    public void schedule(Runnable task, long delaySec) {
        this.generalTaskScheduler.schedule(task, Instant.now().plusSeconds(delaySec));
    }

    public void scheduleAndCancelAfter(String key, Runnable task, long periodSec, long cancelAfterSec) {
        // 启动定时任务
        ScheduledFuture<?> future = this.generalTaskScheduler.scheduleAtFixedRate(
                task,
                Duration.ofSeconds(periodSec)
        );
        this.taskMap.put(key, future);
        // 启动一个一次性的任务, 用于取消定时任务并从 map 中移除 task
        this.schedule(() -> this.cancel(key), cancelAfterSec);
    }

    public void debounce(String key, Runnable task, long delaySec) {
        // 有重复的未执行的task则取消
        this.cancel(key);
        // 规划新的task
        ScheduledFuture<?> newTask = generalTaskScheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("execute debounce task(key:{}, task:{} failed", key, task, e);
            } finally {
                // task 执行完, 将 task 从 map 中移除
                taskMap.remove(key);
            }
        }, Instant.now().plusSeconds(delaySec));
        // 新的task放进map中
        taskMap.put(key, newTask);
    }

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

        public void cancelAfter(String key, Runnable task, long periodSec, long cancelAfterSec) {
            String fullKey = modulePrefix + key;
            debounceService.scheduleAndCancelAfter(fullKey, task, periodSec, cancelAfterSec);
        }

        public void cancel(String key) {
            String fullKey = modulePrefix + key;
            debounceService.cancel(fullKey);
        }
    }
}
