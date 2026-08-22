package com.nl2sql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 自我修正（REQ-513）。
 * 覆盖 TC-513-01（修正成功）、TC-513-02（3 次仍失败返回 null）。
 */
@ExtendWith(MockitoExtension.class)
class SelfCorrectorTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SelfCorrector corrector;

    @BeforeEach
    void setUp() {
        corrector = new SelfCorrector(llmClient, objectMapper);
    }

    @Test
    void correctsOnFirstAttempt() {
        // TC-513-01 修正成功
        when(llmClient.generate(anyString()))
                .thenReturn("{\"sql\":\"SELECT SUM(amount) FROM orders\",\"explanation\":\"fixed\"}");

        String result = corrector.correct("总销售额", "SELECT SUM(amont) FROM orders", "Unknown column amont");

        assertThat(result).isEqualTo("SELECT SUM(amount) FROM orders");
    }

    @Test
    void givesUpAfterThreeFailures() {
        // TC-513-02 3 次仍失败
        when(llmClient.generate(anyString())).thenThrow(new RuntimeException("llm down"));

        String result = corrector.correct("总销售额", "SELECT SUM(amont) FROM orders", "error");

        assertThat(result).isNull();
    }
}
