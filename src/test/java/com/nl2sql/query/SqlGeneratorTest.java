package com.nl2sql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.LlmClient;
import com.nl2sql.llm.dto.LlmResponse;
import com.nl2sql.llm.dto.LlmResult;
import com.nl2sql.llm.dto.LlmUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SqlGenerator 生成与重试（REQ-501、ADR-0002 A3）。
 * 覆盖 TC-501-01、TC-401-02。
 */
@ExtendWith(MockitoExtension.class)
class SqlGeneratorTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SqlGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SqlGenerator(llmClient, objectMapper);
    }

    private static LlmResult llmResult(String content) {
        return new LlmResult(content, new LlmUsage(10, 5, 15));
    }

    @Test
    void generatesSqlFromQuestion() {
        // TC-501-01 验证 REQ-501 SQL 生成
        String content = "{\"sql\":\"SELECT SUM(amount) FROM orders\",\"explanation\":\"total\"}";
        when(llmClient.generate(anyString())).thenReturn(llmResult(content));

        SqlGenerator.SqlGeneration gen = generator.generate("上个月销售额是多少");
        LlmResponse resp = gen.response();

        assertThat(resp.sql()).startsWith("SELECT");
        assertThat(resp.sql()).contains("SUM(amount)");
        assertThat(resp.sql()).contains("orders");
        assertThat(gen.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void retriesOnceOnFormatError() {
        // TC-401-02 上游格式异常：重试 1 次后成功
        when(llmClient.generate(anyString()))
                .thenThrow(new BizException(ErrorCode.LLM_FORMAT_ERROR))
                .thenReturn(llmResult("{\"sql\":\"SELECT 1\",\"explanation\":\"x\"}"));

        SqlGenerator.SqlGeneration gen = generator.generate("q");

        assertThat(gen.response().sql()).isEqualTo("SELECT 1");
        verify(llmClient, times(2)).generate(anyString());
    }

    @Test
    void givesUpAfterOneRetry() {
        // TC-401-02 上游格式异常：重试 1 次仍失败 → 抛 5003
        when(llmClient.generate(anyString()))
                .thenThrow(new BizException(ErrorCode.LLM_FORMAT_ERROR))
                .thenThrow(new BizException(ErrorCode.LLM_FORMAT_ERROR));

        BizException ex = catchThrowableOfType(() -> generator.generate("q"), BizException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LLM_FORMAT_ERROR);
        verify(llmClient, times(2)).generate(anyString());
    }

    @Test
    void timeoutIsNotRetried() {
        // REQ-501 超时（5001）不重试，直接上抛
        when(llmClient.generate(anyString()))
                .thenThrow(new BizException(ErrorCode.LLM_TIMEOUT));

        BizException ex = catchThrowableOfType(() -> generator.generate("q"), BizException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LLM_TIMEOUT);
        verify(llmClient, times(1)).generate(anyString());
    }
}
