package com.nl2sql.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 指标语义层匹配（REQ-506、D-13）。
 * 覆盖 TC-506-01（指标命中）与最长匹配。
 */
@ExtendWith(MockitoExtension.class)
class MetricMatcherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void matchesByMetricName() {
        // TC-506-01 指标命中
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("metric_name", "销售额", "synonyms", "营收,收入,GMV",
                        "template_sql", "SELECT SUM(amount) FROM orders"),
                Map.of("metric_name", "订单量", "synonyms", "订单数",
                        "template_sql", "SELECT COUNT(*) FROM orders")
        ));

        MetricMatcher matcher = new MetricMatcher(jdbcTemplate);
        MetricMatcher.MetricMatch match = matcher.match("总销售额是多少");

        assertThat(match).isNotNull();
        assertThat(match.metricName()).isEqualTo("销售额");
        assertThat(match.templateSql()).isEqualTo("SELECT SUM(amount) FROM orders");
    }

    @Test
    void matchesBySynonym() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("metric_name", "销售额", "synonyms", "营收,收入,GMV",
                        "template_sql", "SELECT SUM(amount) FROM orders")
        ));

        MetricMatcher matcher = new MetricMatcher(jdbcTemplate);
        MetricMatcher.MetricMatch match = matcher.match("今年GMV多少");

        assertThat(match).isNotNull();
        assertThat(match.metricName()).isEqualTo("销售额");
    }

    @Test
    void noMatchReturnsNull() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("metric_name", "销售额", "synonyms", "营收",
                        "template_sql", "SELECT SUM(amount) FROM orders")
        ));

        MetricMatcher matcher = new MetricMatcher(jdbcTemplate);
        MetricMatcher.MetricMatch match = matcher.match("有多少个用户");

        assertThat(match).isNull();
    }

    @Test
    void longestMatchWins() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("metric_name", "销售额");
        row1.put("synonyms", null);
        row1.put("template_sql", "SELECT SUM(amount) FROM orders");

        Map<String, Object> row2 = new HashMap<>();
        row2.put("metric_name", "华东销售额");
        row2.put("synonyms", null);
        row2.put("template_sql", "SELECT SUM(amount) FROM orders WHERE region='华东'");

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row1, row2));

        MetricMatcher matcher = new MetricMatcher(jdbcTemplate);
        MetricMatcher.MetricMatch match = matcher.match("华东销售额是多少");

        assertThat(match).isNotNull();
        assertThat(match.metricName()).isEqualTo("华东销售额");
    }
}
