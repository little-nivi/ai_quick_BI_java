package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResponse;
import org.springframework.stereotype.Component;

/**
 * 意图识别 + 置信度分级（REQ-511、D-16/D-17、ADR D-02）。
 * 分级阈值：≥0.85 高；0.60~0.85 中；<0.60 低。
 */
@Component
public class IntentConfidenceEvaluator {

    public static final double HIGH_THRESHOLD = 0.85;
    public static final double LOW_THRESHOLD = 0.60;

    /**
     * 评估 LLM 结果，返回置信度等级。
     * is_query=false（或 null 视为查询）→ 抛 4001；confidence<0.60 → 抛 4002。
     */
    public String evaluate(LlmResponse llm) {
        if (Boolean.FALSE.equals(llm.isQuery())) {
            throw new BizException(ErrorCode.INTENT_NOT_QUERY);
        }

        double confidence = llm.confidence() == null ? HIGH_THRESHOLD : llm.confidence();
        if (confidence < LOW_THRESHOLD) {
            throw new BizException(ErrorCode.LOW_CONFIDENCE);
        }
        if (confidence >= HIGH_THRESHOLD) {
            return "高";
        }
        return "中";
    }
}
