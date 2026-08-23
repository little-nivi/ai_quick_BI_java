package com.nl2sql.query;

import org.springframework.stereotype.Component;

/**
 * LIMIT 注入（REQ-520、P1-6）：SQL 无 LIMIT 子句时追加 LIMIT 1000，防大结果集。
 */
@Component
public class LimitInjector {

    public String inject(String sql) {
        String upper = sql.toUpperCase();
        if (upper.contains(" LIMIT ")) {
            return sql;
        }
        return sql + " LIMIT 1000";
    }
}
