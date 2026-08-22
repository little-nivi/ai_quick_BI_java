package com.nl2sql.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 业务术语映射（REQ-510）。
 * 将 question 中的 term 替换为 mapping，帮助 qwen 理解口语化表述。
 */
@Component
public class GlossaryMatcher {

    private final JdbcTemplate jdbcTemplate;

    public GlossaryMatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String apply(String question) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT term, mapping FROM business_glossary");
        String result = question;
        for (Map<String, Object> row : rows) {
            String term = (String) row.get("term");
            String mapping = (String) row.get("mapping");
            if (term != null && mapping != null && result.contains(term)) {
                result = result.replace(term, mapping);
            }
        }
        return result;
    }
}
