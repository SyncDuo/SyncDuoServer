package com.syncduo.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
// todo: 1. 新增 restore 表;
public class SyncDuoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncDuoServerApplication.class, args);
    }

}
