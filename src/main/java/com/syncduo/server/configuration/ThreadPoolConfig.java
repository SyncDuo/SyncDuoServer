package com.syncduo.server.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class ThreadPoolConfig implements DisposableBean {

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private final ThreadPoolTaskScheduler scheduledTaskExecutor;

    public ThreadPoolConfig() {
        // async thread pool
        this.threadPoolTaskExecutor = getThreadPoolTaskExecutor();

        // scheduler thread pool
        this.scheduledTaskExecutor = getThreadPoolTaskScheduler();
    }

    private static ThreadPoolTaskScheduler getThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);  // Number of threads for scheduled tasks
        scheduler.setThreadNamePrefix("scheduled-thread-"); // Thread name prefix
        scheduler.initialize();
        return scheduler;
    }

    private static ThreadPoolTaskExecutor getThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Minimum number of threads
        executor.setMaxPoolSize(5); // Maximum number of threads
        executor.setQueueCapacity(50); // Capacity of the queue
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setThreadNamePrefix("Async-"); // Thread name prefix
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    public void changeMinAndMaxThreadsNum(int handlerMinThreads, int handlerMaxThreads) {
        threadPoolTaskExecutor.setCorePoolSize(handlerMinThreads);
        threadPoolTaskExecutor.setMaxPoolSize(handlerMaxThreads);
    }

    @Bean(name = "threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        return threadPoolTaskExecutor;
    }

    // Thread pool for @Scheduled tasks
    @Bean(name = "scheduledTaskExecutor")
    public ThreadPoolTaskScheduler scheduledTaskExecutor() {
        return scheduledTaskExecutor;
    }

    @Override
    public void destroy() {
        log.info("shutdown async task thread pool");
        try {
            // Gracefully shut down the thread pool
            threadPoolTaskExecutor.getThreadPoolExecutor().shutdown();
            // Wait for existing tasks to complete
            if (!threadPoolTaskExecutor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                // If tasks are not completed in the given time, force shutdown
                threadPoolTaskExecutor.getThreadPoolExecutor().shutdownNow();
            }
        } catch (InterruptedException e) {
            // Handle interruption during shutdown
            threadPoolTaskExecutor.getThreadPoolExecutor().shutdownNow();
            Thread.currentThread().interrupt();  // Restore the interrupt status
        }
    }
}
