package com.nl2sql.observability;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Langfuse v4 OTel 上报基础设施（REQ-531、ADR D-35）。
 * 官方已废弃 legacy /api/public/ingestion，Java 端 tracing 唯一支持路径是
 * OpenTelemetry SDK + OTLP/HTTP 导出到 {base}/api/public/otel/v1/traces，
 * 认证 = Basic(base64(pk:sk)) + x-langfuse-ingestion-version: 4。
 * 未配置 key 时整体降级 no-op。
 */
@Component
public class LangfuseOtelTracer {

    private static final Logger log = LoggerFactory.getLogger(LangfuseOtelTracer.class);

    private final boolean enabled;
    private final Tracer tracer;
    private final SdkTracerProvider tracerProvider;

    public LangfuseOtelTracer(
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey,
            @Value("${langfuse.base-url:https://jp.cloud.langfuse.com}") String baseUrl) {

        this.enabled = !publicKey.isBlank() && !secretKey.isBlank();
        if (!this.enabled) {
            this.tracer = null;
            this.tracerProvider = null;
            return; // 未配置 key，no-op（降级）
        }

        String auth = Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        String endpoint = baseUrl.replaceAll("/+$", "") + "/api/public/otel/v1/traces";

        // OTLP 导出失败默认只打 FINE 级日志，等于静默失败（Bug A 教训），包一层结果日志代理
        SpanExporter exporter = new LoggingSpanExporter(OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("Authorization", "Basic " + auth)
                .addHeader("x-langfuse-ingestion-version", "4")
                .setTimeout(Duration.ofSeconds(5))
                .build());

        Resource resource = Resource.getDefault().merge(Resource.builder()
                .put("service.name", "nl2sql-app")
                .build());

        // 1s 批量导出：演示时 trace 准实时出现（默认 5s）；导出在后台线程，不阻塞查询
        this.tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                        .setScheduleDelay(Duration.ofSeconds(1))
                        .build())
                .build();
        this.tracer = tracerProvider.get("nl2sql-observability");

        log.info("langfuse otel exporter initialized: endpoint={}", endpoint);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Tracer tracer() {
        return tracer;
    }

    @PreDestroy
    void shutdown() {
        if (tracerProvider != null) {
            tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    /** 导出结果代理：成功 INFO / 失败 WARN，避免数据静默丢失。 */
    private static final class LoggingSpanExporter implements SpanExporter {
        private final SpanExporter delegate;

        private LoggingSpanExporter(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            CompletableResultCode result = delegate.export(spans);
            result.whenComplete(() -> {
                if (result.isSuccess()) {
                    log.info("langfuse otel exported {} spans", spans.size());
                } else {
                    log.warn("langfuse otel export failed for {} spans (degraded)", spans.size());
                }
            });
            return result;
        }

        @Override
        public CompletableResultCode flush() {
            return delegate.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }
    }
}
