package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

/**
 * SQL 校验（REQ-509、D-15）：AST 级白名单。
 * 根语句必须为 SELECT；否则拦截。消除 M1 正则对字符串字面量的误拦。
 */
@Component
public class SqlValidator {

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.SQL_BLOCKED);
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }

        if (!(statement instanceof Select)) {
            throw new BizException(ErrorCode.SQL_BLOCKED);
        }
    }
}
