package com.syncduo.server.configuration.mybatisplus;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {
    // spring boot 使用的 object mapper
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // 注入到 mybatis plus 使用的 object mapper
        JacksonTypeHandler.setObjectMapper(objectMapper);
    }
}
