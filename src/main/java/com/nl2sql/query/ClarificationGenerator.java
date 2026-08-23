package com.nl2sql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 反问澄清生成（REQ-512、D-18）：低置信时让 LLM 生成澄清问题 + 2~4 个选项。
 */
@Component
public class ClarificationGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClarificationGenerator.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClarificationGenerator(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public Clarification generate(String question) {
        String prompt = "用户的问题含义不明确，请生成一个澄清问题，并给出 2~4 个可能的选项。"
                + "只输出 JSON，结构固定为 {\"question\": \"澄清问题\", \"options\": [\"选项1\", \"选项2\"]}，不要输出其他文字。"
                + "\n\n用户问题：" + question;

        String content = llmClient.generate(prompt).content();
        try {
            Map<String, Object> map = objectMapper.readValue(content, Map.class);
            String q = (String) map.get("question");
            Object optObj = map.get("options");
            List<String> options = optObj instanceof List
                    ? ((List<?>) optObj).stream().map(String::valueOf).toList()
                    : List.of();
            return new Clarification(q, options);
        } catch (Exception e) {
            log.error("clarification parse failed: {}", content, e);
            throw new BizException(ErrorCode.LLM_FORMAT_ERROR);
        }
    }
}
