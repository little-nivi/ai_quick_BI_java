package com.nl2sql.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。兜底默认：未穷举异常 → 5000 + 通用话术 + error 日志（00_总览 §4）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.ok(ApiResponse.error(e.getErrorCode(), TraceIdHolder.getOrCreate()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ResponseEntity.ok(
                ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), detail, TraceIdHolder.getOrCreate()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        String traceId = TraceIdHolder.getOrCreate();
        log.error("[traceId={}] unhandled exception", traceId, e);
        return ResponseEntity.ok(ApiResponse.error(ErrorCode.INTERNAL_ERROR, traceId));
    }
}
