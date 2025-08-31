package com.syncduo.server.service.bussiness;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;


@Slf4j
@Service
public class DebounceService {

    @Qualifier("generalTaskScheduler")
    private final ThreadPoolTaskScheduler generalTaskScheduler;

    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    @Autowired
    public DebounceService(ThreadPoolTaskScheduler generalTaskScheduler) {
        this.generalTaskScheduler = generalTaskScheduler;
    }

    public void scheduleTask(Runnable task, long delaySec) {
        this.generalTaskScheduler.schedule(task, Instant.now().plusSeconds(delaySec));
    }

    public void debounce(String key, Runnable task, long delaySec) {
        // 有重复的未执行的task则取消
        ScheduledFuture<?> existingTask = taskMap.get(key);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
        // 规划新的task
        ScheduledFuture<?> newTask = generalTaskScheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("execute debounce task(key:{}, task:{} failed", key, task, e);
            } finally {
                taskMap.remove(key);
            }
        }, Instant.now().plusSeconds(delaySec));
        // 新的task放进map中
        taskMap.put(key, newTask);
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
            log.debug("{} add task:{}", modulePrefix, task);
            debounceService.debounce(fullKey, task, delaySec);
        }

        public void schedule(Runnable task, long delaySec) {
            debounceService.scheduleTask(task, delaySec);
        }
    }
}
