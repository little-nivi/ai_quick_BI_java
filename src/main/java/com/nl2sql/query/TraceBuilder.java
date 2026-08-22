package com.nl2sql.query;

import com.nl2sql.llm.dto.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据溯源组装（REQ-514）。
 * tables 固定 orders；fields 取执行结果列名；metric 为命中指标名或 null。
 */
@Component
public class TraceBuilder {

    public Trace build(List<String> columns, String matchedMetric, Double confidence, String confidenceLevel) {
        List<String> fields = columns == null ? List.of() : columns;
        return new Trace(List.of("orders"), fields, matchedMetric, confidence, confidenceLevel);
    }
}
