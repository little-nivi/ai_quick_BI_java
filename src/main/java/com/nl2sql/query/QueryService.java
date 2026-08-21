package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询编排：生成 → 校验 → 执行 → 格式化（REQ-504）。
 */
@Service
public class QueryService {

    private final SqlGenerator sqlGenerator;
    private final SqlValidator sqlValidator;
    private final SqlExecutor sqlExecutor;

    public QueryService(SqlGenerator sqlGenerator, SqlValidator sqlValidator, SqlExecutor sqlExecutor) {
        this.sqlGenerator = sqlGenerator;
        this.sqlValidator = sqlValidator;
        this.sqlExecutor = sqlExecutor;
    }

    public QueryResponse query(String question) {
        long start = System.currentTimeMillis();

        // REQ-501 生成
        LlmResponse llm = sqlGenerator.generate(question);
        String sql = llm.sql();

        // REQ-502 校验
        sqlValidator.validate(sql);

        // REQ-503 执行
        SqlExecutor.QueryResult result = sqlExecutor.execute(sql);

        long latencyMs = System.currentTimeMillis() - start;

        return new QueryResponse(question, sql, result.columns(), result.rows(),
                result.rows().size(), latencyMs);
    }

    /** 接口返回 data 结构（REQ-401 正常返回）。 */
    public record QueryResponse(
            String question,
            String sql,
            List<String> columns,
            List<List<Object>> rows,
            int rowCount,
            long latencyMs) {
    }
}
