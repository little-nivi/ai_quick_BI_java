package com.nl2sql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.LlmClient;
import com.nl2sql.llm.dto.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自我修正（REQ-513、D-19）：SQL 执行报错（语法/字段类）回传模型重生成，最多 3 次。
 */
@Component
public class SelfCorrector {

    private static final Logger log = LoggerFactory.getLogger(SelfCorrector.class);

    private static final int MAX_ATTEMPTS = 3;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SelfCorrector(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据原 SQL + 错误信息修正，返回新 SQL；修正失败返回 null（调用方决定兜底）。
     */
    public String correct(String question, String originalSql, String errorMessage) {
        String currentSql = originalSql;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = "以下 SQL 执行时报错，请修正后重新输出。"
                    + "只输出 JSON，结构固定为 {\"sql\": \"...\", \"explanation\": \"...\"}，不要输出其他文字。"
                    + "\n\n用户问题：" + question
                    + "\n原 SQL：" + currentSql
                    + "\n错误信息：" + errorMessage;

            try {
                String content = llmClient.generate(prompt).content();
                LlmResponse resp = objectMapper.readValue(content, LlmResponse.class);
                if (resp.sql() != null && !resp.sql().isBlank()) {
                    return resp.sql();
                }
            } catch (Exception e) {
                log.warn("self-correct attempt {} failed", attempt, e);
            }
        }
        return null;
    }
}
