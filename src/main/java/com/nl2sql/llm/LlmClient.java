package com.nl2sql.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResult;
import com.nl2sql.llm.dto.LlmUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * 调用通义千问生成内容，返回模型输出文本 + token 用量（REQ-501/532）。
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
     * 发送 prompt，返回模型生成的文本 + token 用量。
     * 超时 → 5001；HTTP 层异常 → 5003（格式异常，由上层决定是否重试）。
     */
    public LlmResult generate(String prompt) {
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

        return parse(responseBody);
    }

    private LlmResult parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new BizException(ErrorCode.LLM_FORMAT_ERROR);
            }
            JsonNode usage = root.path("usage");
            LlmUsage u = new LlmUsage(
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0));
            return new LlmResult(content.asText(), u);
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
