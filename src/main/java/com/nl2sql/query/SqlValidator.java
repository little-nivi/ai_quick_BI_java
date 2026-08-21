package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * SQL 校验：安全白名单 + 语法预解析（REQ-502、SR-1）。
 */
@Component
public class SqlValidator {

    /** 高危关键字白名单（ADR-0002 C5）。 */
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(DROP|DELETE|UPDATE|ALTER|TRUNCATE|INSERT|CREATE)\\b");

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.SQL_BLOCKED);
        }

        String normalized = sql.replaceAll("\\s+", " ").trim();
        String upper = normalized.toUpperCase();

        // SR-1：高危关键字 或 非 SELECT 开头 → 拦截
        if (DANGEROUS.matcher(upper).find() || !upper.startsWith("SELECT")) {
            throw new BizException(ErrorCode.SQL_BLOCKED);
        }

        // REQ-502 语法预解析（JSqlParser）
        try {
            CCJSqlParserUtil.parse(normalized);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
