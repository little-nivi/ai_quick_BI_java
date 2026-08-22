package com.nl2sql.query;

import com.nl2sql.auth.UserContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 权限注入（REQ-507、ADR D-12、SR-4）。
 * data_scope != "all" 时，在 SQL 的 WHERE 子句强制追加 region 过滤。
 * 区域值经单引号转义，防注入；不信任模型输出。
 */
@Component
public class PermissionInjector {

    public String inject(String sql) {
        UserContext.AuthUser user = UserContext.get();
        if (user == null || user.dataScope() == null || "all".equals(user.dataScope())) {
            return sql;
        }

        String regionList = Arrays.stream(user.dataScope().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::quote)
                .collect(Collectors.joining(","));

        if (regionList.isEmpty()) {
            return sql;
        }

        String upper = sql.toUpperCase();
        String clause = " AND region IN (" + regionList + ")";
        if (upper.contains(" WHERE ")) {
            return sql + clause;
        }
        if (upper.contains(" GROUP BY ")) {
            int idx = upper.indexOf(" GROUP BY ");
            return sql.substring(0, idx) + " WHERE region IN (" + regionList + ")" + sql.substring(idx);
        }
        if (upper.contains(" ORDER BY ")) {
            int idx = upper.indexOf(" ORDER BY ");
            return sql.substring(0, idx) + " WHERE region IN (" + regionList + ")" + sql.substring(idx);
        }
        return sql + " WHERE region IN (" + regionList + ")";
    }

    private String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
