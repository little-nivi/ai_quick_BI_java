package com.nl2sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端集成测试（REQ-406）：连真实 MySQL + qwen，走通「登录 → 问数 → 返回」完整链路。
 *
 * 标记 @Tag("integration")，surefire 默认排除；手动触发：
 *   mvn test -Dgroups=integration
 * 需先满足：
 *   1. Docker MySQL 已启动（DB_PASSWORD 正确）
 *   2. 设置环境变量 DASHSCOPE_API_KEY
 *   3. orders 表已有数据（运行 scripts/generate_orders.py）
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryEndToEndIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void fullQueryChainReturnsSuccess() {
        String base = "http://127.0.0.1:" + port;

        // 1. 登录拿 token
        String token = login(base, "admin", "admin123");

        // 2. 带 token 问数
        JsonNode data = query(base, token, "总销售额是多少");

        // 3. 断言链路结果
        assertThat(data.path("code").asInt()).isEqualTo(2000);
        assertThat(data.path("data").path("sql").asText()).startsWith("SELECT");
        assertThat(data.path("data").path("rowCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    private String login(String base, String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(
                Map.of("username", username, "password", password), headers);
        try {
            String body = restTemplate.postForObject(base + "/api/v1/auth/login", req, String.class);
            JsonNode root = objectMapper.readTree(body);
            assertThat(root.path("code").asInt()).isEqualTo(2000);
            return root.path("data").path("token").asText();
        } catch (Exception e) {
            throw new RuntimeException("login failed", e);
        }
    }

    private JsonNode query(String base, String token, String question) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("question", question), headers);
        try {
            String body = restTemplate.postForObject(base + "/api/v1/query", req, String.class);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("query failed", e);
        }
    }
}
