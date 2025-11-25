package com.syncduo.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@EnableAsync
// todo: 2. 3-2-1 备份功能(3份数据, 一冷一热一异地)
// todo: 1. 增加对 danging snapshot 的删除
// todo: 3. 增加 log 实时传输
public class SyncDuoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncDuoServerApplication.class, args);
    }

}
