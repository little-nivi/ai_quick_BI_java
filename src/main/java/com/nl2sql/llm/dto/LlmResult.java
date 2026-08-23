package com.nl2sql.llm.dto;

/**
 * LLM 调用结果：内容 + token 用量（REQ-532）。
 */
public record LlmResult(String content, LlmUsage usage) {
}
