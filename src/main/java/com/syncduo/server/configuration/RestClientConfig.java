package com.syncduo.server.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient rcloneRestClient(
            @Value("${syncduo.server.rclone.httpBaseUrl}") String baseUrl,
            @Value("${syncduo.server.rclone.httpUser}") String user,
            @Value("${syncduo.server.rclone.httpPassword}") String password) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(user, password);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .build();
    }
}
