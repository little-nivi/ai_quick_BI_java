package com.nl2sql.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.llm.dto.LlmUsage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Langfuse 观测组装（REQ-531/533、ADR D-34/D-36）。
 * v4 数据模型：一次查询 = 一条 OTel trace = root span（整体输入输出）
 * + generation 子 span（模型 / token 用量），属性映射见
 * https://langfuse.com/integrations/native/opentelemetry 。
 * trace 级 metadata 复制到每个 span：v4 按 observation 查询过滤，
 * 仅 root 持有的属性无法在子项上过滤/聚合。
 */
@Component
public class LangfuseObservability {

    private static final Logger log = LoggerFactory.getLogger(LangfuseObservability.class);

    private final LangfuseOtelTracer langfuse;
    private final ObjectMapper objectMapper;

    public LangfuseObservability(LangfuseOtelTracer langfuse, ObjectMapper objectMapper) {
        this.langfuse = langfuse;
        this.objectMapper = objectMapper;
    }

    public void record(String question, String generatedSql, Long latencyMs,
                       boolean cacheHit, String confidenceLevel, String matchedMetric,
                       String role, String model, LlmUsage usage) {
        if (!langfuse.isEnabled()) {
            return; // 未配置 key，no-op（降级）
        }
        try {
            Tracer tracer = langfuse.tracer();
            Attributes traceMeta = Attributes.builder()
                    .put("langfuse.trace.metadata.latency_ms", String.valueOf(latencyMs == null ? 0 : latencyMs))
                    .put("langfuse.trace.metadata.cache_hit", String.valueOf(cacheHit))
                    .put("langfuse.trace.metadata.confidence_level", confidenceLevel == null ? "" : confidenceLevel)
                    .put("langfuse.trace.metadata.matched_metric", matchedMetric == null ? "" : matchedMetric)
                    .put("langfuse.trace.metadata.role", role == null ? "" : role)
                    .build();

            Span root = tracer.spanBuilder("nl2sql-query")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute("langfuse.trace.name", "nl2sql-query")
                    .setAttribute("langfuse.observation.input", nullToEmpty(question))
                    .setAllAttributes(traceMeta)
                    .startSpan();
            try (Scope ignored = root.makeCurrent()) {
                // 在 root 作用域内创建 → 自动成为 root 的子 observation（评估器 isRootObservation 对应 root）
                Span generation = tracer.spanBuilder("sql-generation")
                        .setAttribute("langfuse.observation.type", "generation")
                        .setAttribute("langfuse.observation.input", nullToEmpty(question))
                        .setAttribute("langfuse.observation.output", nullToEmpty(generatedSql))
                        .setAttribute("langfuse.observation.model.name",
                                model == null || model.isBlank() ? "qwen-turbo" : model)
                        .setAttribute("langfuse.observation.usage_details", usageJson(usage))
                        .setAllAttributes(traceMeta)
                        .startSpan();
                generation.end();
            } finally {
                root.setAttribute("langfuse.observation.output", nullToEmpty(generatedSql));
                root.end();
            }
        } catch (Exception e) {
            log.warn("langfuse record failed (degraded, ignored)", e);
        }
    }

    /** token 用量 → Langfuse usage_details JSON：{"input":..,"output":..,"total":..}。 */
    private String usageJson(LlmUsage usage) throws Exception {
        if (usage == null) {
            usage = new LlmUsage(0, 0, 0);
        }
        return objectMapper.writeValueAsString(Map.of(
                "input", usage.promptTokens(),
                "output", usage.completionTokens(),
                "total", usage.totalTokens()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
