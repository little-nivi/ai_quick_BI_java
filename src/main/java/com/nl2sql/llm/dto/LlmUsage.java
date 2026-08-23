package com.nl2sql.llm.dto;

/**
 * qwen 返回的 token 用量（REQ-532）。
 */
public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {
}
