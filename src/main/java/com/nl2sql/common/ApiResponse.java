package com.nl2sql.common;

/**
 * 统一响应结构 {@code { code, message, data, traceId }}。
 * REQ-401、全局错误码规范（00_总览 §4）。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, traceId);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String traceId) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, traceId);
    }

    public static <T> ApiResponse<T> error(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
