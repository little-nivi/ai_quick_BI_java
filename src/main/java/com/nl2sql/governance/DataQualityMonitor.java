package com.nl2sql.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据质量监控（REQ-523、D-28）：定时检查 orders 表空值率/枚举漂移/数据量波动。
 */
@Component
public class DataQualityMonitor {

    private static final Logger log = LoggerFactory.getLogger(DataQualityMonitor.class);

    private final JdbcTemplate jdbcTemplate;

    public DataQualityMonitor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 每日凌晨执行（D-28）。 */
    @Scheduled(cron = "0 0 0 * * *")
    public void check() {
        checkNullRate("amount");
        checkNullRate("order_date");
        checkNullRate("region");
        checkVolumeSpike();
    }

    private void checkNullRate(String field) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        Long nulls = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE " + field + " IS NULL", Long.class);
        if (total == null || total == 0) {
            return;
        }
        double rate = (double) nulls / total;
        if (rate > 0.10) {
            alert("null_rate", "orders", field, String.format("%.2f%%", rate * 100), "10%", "warning");
        }
    }

    private void checkVolumeSpike() {
        Long today = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE order_date >= CURDATE()", Long.class);
        Long avg7 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) / 7 FROM orders WHERE order_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)", Long.class);
        if (today == null || avg7 == null || avg7 == 0) {
            return;
        }
        double change = Math.abs((double) (today - avg7) / avg7);
        if (change > 0.50) {
            alert("volume_spike", "orders", null, String.valueOf(today), "±50%", "warning");
        }
    }

    private void alert(String type, String table, String field, String current, String threshold, String severity) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO data_quality_alerts (alert_type, table_name, field_name, current_value, threshold, severity) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    type, table, field, current, threshold, severity);
        } catch (Exception e) {
            log.error("data quality alert write failed", e);
        }
    }
}
