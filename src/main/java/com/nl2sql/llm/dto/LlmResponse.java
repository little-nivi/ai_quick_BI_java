package com.nl2sql.llm.dto;

/**
 * qwen 输出 JSON 结构（ADR-0002 C3）。
 */
public record LlmResponse(String sql, String explanation) {
}
