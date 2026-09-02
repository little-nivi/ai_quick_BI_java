package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResponse;
import org.springframework.stereotype.Component;

/**
 * 意图识别 + 置信度分级（REQ-511、D-16/D-17、ADR D-02）。
 * 分级阈值：≥0.85 高；0.75~0.85 中；<0.75 低（原为 0.60，qwen-turbo 打分偏乐观因此上调到 0.75）。
 * 澄清兜底判定已移到 {@link ClarificationDecider}，这里只做 is_query + 置信度文本分级。
 */
@Component
public class IntentConfidenceEvaluator {

    public static final double HIGH_THRESHOLD = 0.85;
    public static final double LOW_THRESHOLD = 0.75;

    /**
     * 评估 LLM 结果。
     * is_query=false → 抛 4001（调用方会再用 ClarificationDecider 决定是否降级为 4002 澄清）；
     * confidence<0.75 → 抛 4002。
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
