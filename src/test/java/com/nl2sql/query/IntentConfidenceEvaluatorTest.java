package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 意图识别 + 置信度分级（REQ-511）。
 * 覆盖 TC-404-01（非问数）、TC-404-02（低置信）、TC-404-03（高置信）、TC-404-04（中置信）。
 */
class IntentConfidenceEvaluatorTest {

    private final IntentConfidenceEvaluator evaluator = new IntentConfidenceEvaluator();

    @Test
    void nonQueryReturns4001() {
        // TC-404-01
        LlmResponse llm = new LlmResponse("", "", false, 0.9);
        BizException ex = catchThrowableOfType(() -> evaluator.evaluate(llm), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INTENT_NOT_QUERY);
    }

    @Test
    void lowConfidenceReturns4002() {
        // TC-404-02
        LlmResponse llm = new LlmResponse("SELECT 1", "", true, 0.4);
        BizException ex = catchThrowableOfType(() -> evaluator.evaluate(llm), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOW_CONFIDENCE);
    }

    @Test
    void highConfidencePasses() {
        // TC-404-03
        LlmResponse llm = new LlmResponse("SELECT 1", "", true, 0.95);
        assertThat(evaluator.evaluate(llm)).isEqualTo("高");
    }

    @Test
    void mediumConfidencePasses() {
        // TC-404-04
        LlmResponse llm = new LlmResponse("SELECT 1", "", true, 0.75);
        assertThat(evaluator.evaluate(llm)).isEqualTo("中");
    }

    @Test
    void nullConfidenceDefaultsToHigh() {
        LlmResponse llm = new LlmResponse("SELECT 1", "", true, null);
        assertThat(evaluator.evaluate(llm)).isEqualTo("高");
    }
}
