package com.nl2sql.query;

import com.nl2sql.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 会话写穿透（REQ-519、D-24）：每次 Q&A 落库 session_records，写失败不阻断主链路。
 */
@Component
public class SessionRecorder {

    private static final Logger log = LoggerFactory.getLogger(SessionRecorder.class);

    private final JdbcTemplate jdbcTemplate;

    public SessionRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String question, String generatedSql, boolean cacheHit) {
        UserContext.AuthUser user = UserContext.get();
        Long userId = user == null ? 0L : user.userId();
        try {
            jdbcTemplate.update(
                    "INSERT INTO session_records (user_id, question, generated_sql, cache_hit) VALUES (?, ?, ?, ?)",
                    userId, question, generatedSql, cacheHit);
        } catch (Exception e) {
            log.error("session record write failed, question={}", question, e);
        }
    }
}
