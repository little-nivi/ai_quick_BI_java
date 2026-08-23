package com.nl2sql.query;

import com.nl2sql.auth.UserContext;
import com.nl2sql.common.ApiResponse;
import com.nl2sql.common.TraceIdHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 历史记录查询接口（REQ-526）：返回当前用户最近的 session_records（≤50 条）。
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final JdbcTemplate jdbcTemplate;

    public SessionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> list() {
        String traceId = TraceIdHolder.getOrCreate();
        UserContext.AuthUser user = UserContext.get();
        Long userId = user == null ? 0L : user.userId();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT question, generated_sql, cache_hit, created_at " +
                        "FROM session_records WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
                userId);
        return ApiResponse.ok(rows, traceId);
    }
}
