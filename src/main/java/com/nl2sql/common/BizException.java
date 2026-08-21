package com.nl2sql.common;

/**
 * 业务异常：携带错误码与 traceId，由 {@link GlobalExceptionHandler} 统一捕获。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
