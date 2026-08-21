package com.nl2sql.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * qwen REST 客户端配置（ADR-0001 D-09：M1 用 REST 直连 DashScope OpenAI 兼容端点）。
 */
@Configuration
public class LlmConfig {

    @Bean
    public RestClient llmRestClient(
            @Value("${nl2sql.llm.base-url}") String baseUrl,
            @Value("${nl2sql.llm.api-key}") String apiKey,
            @Value("${nl2sql.llm.timeout-seconds:30}") int timeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds)); // NFR-2

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
