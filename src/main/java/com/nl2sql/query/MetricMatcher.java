package com.nl2sql.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 指标语义层匹配（REQ-506、ADR D-13）。
 * 命中规则：metric_name 或任一 synonym（逗号分隔）完整子串出现在问题中；
 * 多指标命中取最长匹配者。
 */
@Component
public class MetricMatcher {

    private final JdbcTemplate jdbcTemplate;

    public MetricMatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 命中结果；null 表示未命中（走 NL2SQL）。 */
    public MetricMatch match(String question) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT metric_name, synonyms, template_sql FROM metric_definitions WHERE status = 'published'");

        MetricMatch best = null;
        int bestLen = 0;
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("metric_name");
            String synonyms = (String) row.get("synonyms");

            if (name != null && question.contains(name) && name.length() > bestLen) {
                bestLen = name.length();
                best = new MetricMatch(name, (String) row.get("template_sql"));
            }
            if (synonyms != null) {
                for (String syn : synonyms.split(",")) {
                    String s = syn.trim();
                    if (!s.isEmpty() && question.contains(s) && s.length() > bestLen) {
                        bestLen = s.length();
                        best = new MetricMatch(name, (String) row.get("template_sql"));
                    }
                }
            }
        }
        return best;
    }

    /** 命中结果：指标名 + 模板 SQL。 */
    public record MetricMatch(String metricName, String templateSql) {
    }
}
