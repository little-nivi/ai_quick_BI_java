package com.nl2sql.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * qwen 输出 JSON 结构（M3 升级，ADR-0005 B1）。
 * is_query / confidence 为可空包装类型，容忍模型偶发缺字段。
 * 注意：LLM 输出 snake_case 的 is_query，需显式映射到 camelCase 组件 isQuery。
 */
public record LlmResponse(
        String sql,
        String explanation,
        @JsonProperty("is_query") Boolean isQuery,
        Double confidence) {
}
