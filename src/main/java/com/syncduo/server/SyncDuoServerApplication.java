package com.syncduo.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableTransactionManagement
// todo: 1. rclone 使用 binary 集成; 2. 去除 syncsetting 表, 新增 restore 表; 3. 增加 debounceService, 精简代码;
public class SyncDuoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncDuoServerApplication.class, args);
    }

}
