package com.syncduo.server.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Slf4j
public class ThreadPoolConfig {

    @Bean(name = "generalTaskScheduler")
    public ThreadPoolTaskScheduler generalTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(30);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("General-Task-Scheduled-Thread-"); // Thread name prefix
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    // 注解启动的定时任务, 使用这个
    @Bean(name = "systemManagementTaskScheduler")
    public ThreadPoolTaskScheduler systemManagementTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("System-Management-Thread-"); // Thread name prefix
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
