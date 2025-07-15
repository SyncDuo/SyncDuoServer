package com.syncduo.server.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Slf4j
public class ThreadPoolConfig {

    private final ThreadPoolTaskScheduler systemManagementTaskScheduler;

    private final ThreadPoolTaskScheduler generalTaskScheduler;

    private final ThreadPoolTaskScheduler rcloneTaskScheduler;

    private final ThreadPoolTaskScheduler filesystemEventDebounceScheduler;

    public ThreadPoolConfig() {

        // filesystem event handler thread pool
        this.filesystemEventDebounceScheduler = getFilesystemEventDebounceScheduler();

        // generalTaskScheduler
        this.generalTaskScheduler = getGeneralTaskScheduler();

        // rclone job management schedule thread pool
        this.rcloneTaskScheduler = getRcloneTaskScheduler();

        // scheduler thread pool
        this.systemManagementTaskScheduler = getSystemManagementTaskScheduler();
    }

    private static ThreadPoolTaskScheduler getFilesystemEventDebounceScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("Filesystem-Event-Debounce-Thread-"); // Thread name prefix
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    private static ThreadPoolTaskScheduler getGeneralTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("General-Task-Scheduled-Thread-"); // Thread name prefix
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    private static ThreadPoolTaskScheduler getRcloneTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("Rclone-Task-Scheduled-Thread-"); // Thread name prefix
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    private static ThreadPoolTaskScheduler getSystemManagementTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("System-Management-Thread-"); // Thread name prefix
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    // Thread pool for filesystem event handler
    @Bean(name = "filesystemEventDebounceScheduler")
    public ThreadPoolTaskScheduler filesystemEventDebounceScheduler() {
        return this.filesystemEventDebounceScheduler;
    }

    @Bean(name = "generalTaskScheduler")
    public ThreadPoolTaskScheduler generalTaskScheduler() {
        return this.generalTaskScheduler;
    }

    // Thread pool for @Scheduled tasks
    @Bean(name = "systemManagementTaskScheduler")
    public ThreadPoolTaskScheduler systemManagementTaskScheduler() {
        return this.systemManagementTaskScheduler;
    }

    // Thread pool for rclone job management tasks
    @Bean(name = "rcloneTaskScheduler")
    public ThreadPoolTaskScheduler rcloneTaskScheduler() {
        return this.rcloneTaskScheduler;
    }
}
