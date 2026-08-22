package com.nl2sql.query;

import com.nl2sql.common.BizException;

/**
 * 低置信反问异常：携带澄清信息，由 {@code GlobalExceptionHandler} 转 4002 + data.clarification。
 */
public class ClarifyException extends BizException {

    private final Clarification clarification;

    public ClarifyException(Clarification clarification) {
        super(com.nl2sql.common.ErrorCode.LOW_CONFIDENCE);
        this.clarification = clarification;
    }

    public Clarification getClarification() {
        return clarification;
    }
}
