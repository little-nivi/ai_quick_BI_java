package com.nl2sql.query;

import com.nl2sql.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 审计日志落库（REQ-508、ADR D-14：同步写，写失败不阻断主链路）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String question, String generatedSql, Long latencyMs, String ipAddress) {
        UserContext.AuthUser user = UserContext.get();
        Long userId = user == null ? null : user.userId();
        String role = user == null ? null : user.role();

        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, role, question, generated_sql, confidence, " +
                            "latency_ms, token_used, cache_hit, ip_address) " +
                            "VALUES (?, ?, ?, ?, NULL, ?, NULL, false, ?)",
                    userId, role, question, generatedSql, latencyMs, ipAddress);
        } catch (Exception e) {
            log.error("audit log write failed, question={}", question, e);
        }
    }
}
