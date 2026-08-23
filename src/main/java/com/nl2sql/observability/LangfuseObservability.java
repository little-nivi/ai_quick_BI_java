package com.nl2sql.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.llm.dto.LlmUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LangFuse 观测组装 + 异步发送（REQ-531/533、ADR D-34/D-36）。
 * 组装 trace（问题/SQL/耗时/置信度/缓存） + generation（token 用量/模型），异步发送，失败静默降级。
 */
@Component
public class LangfuseObservability {

    private static final Logger log = LoggerFactory.getLogger(LangfuseObservability.class);

    private final LangfuseClient client;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LangfuseObservability(LangfuseClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public void record(String question, String generatedSql, Long latencyMs,
                       boolean cacheHit, String confidenceLevel, String matchedMetric,
                       String role, String model, LlmUsage usage) {
        if (!client.isEnabled()) {
            return; // 未配置 key，no-op（降级）
        }
        executor.submit(() -> {
            try {
                String body = buildBatch(question, generatedSql, latencyMs, cacheHit,
                        confidenceLevel, matchedMetric, role, model, usage);
                client.send(body);
            } catch (Exception e) {
                log.warn("langfuse record failed (degraded, ignored)", e);
            }
        });
    }

    private String buildBatch(String question, String sql, Long latencyMs, boolean cacheHit,
                              String confidenceLevel, String matchedMetric, String role,
                              String model, LlmUsage usage) throws Exception {
        String traceId = UUID.randomUUID().toString();
        String genId = UUID.randomUUID().toString();
        // LangFuse 要求毫秒精度 ISO-8601（3 位小数），截断纳秒
        String now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();

        Map<String, Object> traceBody = Map.of(
                "name", "nl2sql-query",
                "input", question == null ? "" : question,
                "output", sql == null ? "" : sql,
                "metadata", Map.of(
                        "latency_ms", latencyMs == null ? 0 : latencyMs,
                        "cache_hit", cacheHit,
                        "confidence_level", confidenceLevel == null ? "" : confidenceLevel,
                        "matched_metric", matchedMetric == null ? "" : matchedMetric,
                        "role", role == null ? "" : role));

        Map<String, Object> genBody = Map.of(
                "id", genId,
                "name", "sql-generation",
                "model", model == null ? "qwen-turbo" : model,
                "input", question == null ? "" : question,
                "output", sql == null ? "" : sql,
                "usage", Map.of(
                        "input", usage == null ? 0 : usage.promptTokens(),
                        "output", usage == null ? 0 : usage.completionTokens(),
                        "total", usage == null ? 0 : usage.totalTokens()));

        Map<String, Object> batch = Map.of(
                "batch", List.of(
                        Map.of("id", traceId, "type", "trace-create", "timestamp", now, "body", traceBody),
                        Map.of("id", genId, "type", "generation-create", "timestamp", now,
                                "traceId", traceId, "body", genBody)
                ));

        return objectMapper.writeValueAsString(batch);
    }
}
