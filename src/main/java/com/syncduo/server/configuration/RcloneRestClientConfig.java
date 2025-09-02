package com.syncduo.server.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RcloneRestClientConfig {

    @Value("${syncduo.server.rclone.httpBaseUrl}")
    private String BASE_URL;

    @Value("${syncduo.server.rclone.httpUser}")
    private String USER;

    @Value("${syncduo.server.rclone.httpPassword}")
    private String PASSWORD;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(USER, PASSWORD);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .build();
    }
}
