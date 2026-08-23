package com.nl2sql.common;

/**
 * 全局错误码（00_总览 §4、ADR-0001 §附）。
 */
public enum ErrorCode {

    SUCCESS(2000, "success"),
    PARAM_INVALID(4000, "参数校验失败"),
    INTENT_NOT_QUERY(4001, "意图识别非问数"),
    LOW_CONFIDENCE(4002, "置信度低，需澄清"),
    SQL_BLOCKED(4003, "SQL 安全拦截"),
    FORBIDDEN(4004, "权限不足"),
    RATE_LIMITED(4290, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(5000, "系统内部错误"),
    LLM_TIMEOUT(5001, "系统繁忙，请稍后重试"),
    DB_TIMEOUT(5002, "查询超时，请缩小范围"),
    LLM_FORMAT_ERROR(5003, "服务暂时不可用，请稍后重试"),
    UNAUTHORIZED(401, "未授权"),
    AUTH_FAILED(40101, "用户名或密码错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
