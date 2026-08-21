package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读连接执行 SQL + 超时控制（REQ-503、SR-2）。
 * 数据源已配置 read-only=true；此处再显式设置 queryTimeout。
 */
@Component
public class SqlExecutor {

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QueryResult execute(String sql) {
        try {
            return jdbcTemplate.execute((Statement statement) -> {
                statement.setQueryTimeout(30); // NFR-4
                try (ResultSet rs = statement.executeQuery(sql)) {
                    return extract(rs);
                }
            });
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            if (hasTimeout(e)) {
                throw new BizException(ErrorCode.DB_TIMEOUT);
            }
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static QueryResult extract(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows);
    }

    private boolean hasTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SQLTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /** 查询结果：列名 + 行数据。 */
    public record QueryResult(List<String> columns, List<List<Object>> rows) {
    }
}
