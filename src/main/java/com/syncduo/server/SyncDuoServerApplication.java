package com.syncduo.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncDuoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncDuoServerApplication.class, args);
    }

}
