package com.nl2sql.governance;

import com.nl2sql.auth.UserContext;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 指标口径审批流（REQ-524、D-29、SR-9）：创建(draft) → 审批(published) → 废弃(deprecated)。
 * 仅 admin 可审批/废弃（SR-9）。
 */
@Service
public class MetricGovernanceService {

    private final JdbcTemplate jdbcTemplate;

    public MetricGovernanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** REQ-410 查询全部指标，按 id 倒序（新指标在前）。 */
    public List<Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList(
                "SELECT metric_id, metric_name, synonyms, expression, related_tables, definition, " +
                        "template_sql, version, status, approved_by, approved_at, created_by " +
                        "FROM metric_definitions ORDER BY metric_id DESC");
    }

    public long create(String metricName, String synonyms, String expression,
                       String relatedTables, String definition, String templateSql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO metric_definitions (metric_name, synonyms, expression, related_tables, " +
                            "definition, template_sql, version, status, created_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 1, 'draft', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, metricName);
            ps.setString(2, synonyms);
            ps.setString(3, expression);
            ps.setString(4, relatedTables);
            ps.setString(5, definition);
            ps.setString(6, templateSql);
            ps.setObject(7, currentUserId());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public void approve(long metricId) {
        requireAdmin();
        int updated = jdbcTemplate.update(
                "UPDATE metric_definitions SET status = 'published', approved_by = ?, approved_at = CURRENT_TIMESTAMP " +
                        "WHERE metric_id = ? AND status = 'draft'",
                currentUserId(), metricId);
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
    }

    public void deprecate(long metricId) {
        requireAdmin();
        int updated = jdbcTemplate.update(
                "UPDATE metric_definitions SET status = 'deprecated' WHERE metric_id = ? AND status = 'published'",
                metricId);
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        // REQ-525 血缘通知：有下游依赖则写告警
        Integer lineageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM metric_lineage WHERE metric_id = ?", Integer.class, metricId);
        if (lineageCount != null && lineageCount > 0) {
            jdbcTemplate.update(
                    "INSERT INTO data_quality_alerts (alert_type, table_name, field_name, severity) " +
                            "VALUES ('metric_lineage_notice', 'metric_definitions', ?, 'warning')",
                    String.valueOf(metricId));
        }
    }

    private void requireAdmin() {
        UserContext.AuthUser user = UserContext.get();
        if (user == null || !"admin".equals(user.role())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Object currentUserId() {
        UserContext.AuthUser user = UserContext.get();
        return user == null ? null : user.userId();
    }
}
