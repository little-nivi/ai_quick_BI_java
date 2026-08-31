package com.nl2sql.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * LangFuse ingestion API REST 直连（REQ-531、ADR D-35）。
 * 端点：POST {base}/api/public/ingestion，Basic auth(public:secret)。
 */
@Component
public class LangfuseClient {

    private static final Logger log = LoggerFactory.getLogger(LangfuseClient.class);

    private final RestClient restClient;
    private final String authHeader;
    private final boolean enabled;

    public LangfuseClient(
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey,
            @Value("${langfuse.base-url:https://jp.cloud.langfuse.com}") String baseUrl) {

        this.enabled = !publicKey.isBlank() && !secretKey.isBlank();
        if (this.enabled) {
            String raw = publicKey + ":" + secretKey;
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
        } else {
            this.authHeader = null;
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 发送一批观测事件；打印响应便于排错；失败仅记 WARN（降级）。 */
    public void send(String batchBody) {
        if (!enabled) {
            return;
        }
        try {
            // ingestion 返回 207 表示"已入队"（异步处理）；body 里含 successes/errors 明细
            String resp = restClient.post()
                    .uri("/api/public/ingestion")
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .body(batchBody)
                    .retrieve()
                    .body(String.class);
            log.info("langfuse batch accepted: {}", resp);
        } catch (HttpStatusCodeException e) {
            log.warn("langfuse send rejected: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("langfuse send failed (degraded, ignored)", e);
        }
    }
}
