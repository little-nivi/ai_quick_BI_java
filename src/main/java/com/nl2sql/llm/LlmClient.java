package com.nl2sql.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * 调用通义千问生成内容，返回模型输出的文本（REQ-501）。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public LlmClient(RestClient llmRestClient,
                     ObjectMapper objectMapper,
                     @Value("${nl2sql.llm.model:qwen-turbo}") String model) {
        this.restClient = llmRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    /**
     * 发送 prompt，返回模型生成的文本内容。
     * 超时 → 5001；HTTP 层异常 → 5003（格式异常，由上层决定是否重试）。
     */
    public String generate(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            if (hasTimeout(e)) {
                log.error("llm timeout", e);
                throw new BizException(ErrorCode.LLM_TIMEOUT);
            }
            log.error("llm http error", e);
            throw new BizException(ErrorCode.LLM_FORMAT_ERROR);
        }

        return extractContent(responseBody);
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new BizException(ErrorCode.LLM_FORMAT_ERROR);
            }
            return content.asText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("llm response not json or missing choices", e);
            throw new BizException(ErrorCode.LLM_FORMAT_ERROR);
        }
    }

    private boolean hasTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
