package com.nl2sql.governance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nl2sql.common.ApiResponse;
import com.nl2sql.common.TraceIdHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 指标管理接口（REQ-407/408/409，admin 权限由 Service 层强制 SR-9）。
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    private final MetricGovernanceService service;

    public MetricController(MetricGovernanceService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody MetricRequest request) {
        String traceId = TraceIdHolder.getOrCreate();
        long metricId = service.create(request.metricName(), request.synonyms(), request.expression(),
                request.relatedTables(), request.definition(), request.templateSql());
        return ApiResponse.ok(Map.of("metric_id", metricId, "status", "draft"), traceId);
    }

    @PostMapping("/{metricId}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable long metricId) {
        String traceId = TraceIdHolder.getOrCreate();
        service.approve(metricId);
        return ApiResponse.ok(Map.of("status", "published"), traceId);
    }

    @PostMapping("/{metricId}/deprecate")
    public ApiResponse<Map<String, Object>> deprecate(@PathVariable long metricId) {
        String traceId = TraceIdHolder.getOrCreate();
        service.deprecate(metricId);
        return ApiResponse.ok(Map.of("status", "deprecated"), traceId);
    }

    public record MetricRequest(
            @NotBlank(message = "metric_name 不能为空") @Size(max = 100)
            @JsonProperty("metric_name") String metricName,
            @Size(max = 500) @JsonProperty("synonyms") String synonyms,
            @NotBlank(message = "expression 不能为空") @JsonProperty("expression") String expression,
            @NotBlank(message = "related_tables 不能为空") @JsonProperty("related_tables") String relatedTables,
            @NotBlank(message = "definition 不能为空") @JsonProperty("definition") String definition,
            @NotBlank(message = "template_sql 不能为空") @JsonProperty("template_sql") String templateSql) {
    }
}
