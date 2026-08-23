package com.nl2sql.query;

import com.nl2sql.cache.SemanticCache;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 降级链（REQ-518、D-23）：模型 API 超时 → 语义缓存兜底 → 友好提示。
 */
@Component
public class DegradationHandler {

    private final SemanticCache semanticCache;

    public DegradationHandler(SemanticCache semanticCache) {
        this.semanticCache = semanticCache;
    }

    /** 模型超时后尝试语义缓存兜底；命中返回缓存结果，未命中抛 5001。 */
    public Object degrade(String question) {
        Object cached = semanticCache.get(question);
        if (cached != null) {
            return cached;
        }
        throw new BizException(ErrorCode.LLM_TIMEOUT);
    }
}
