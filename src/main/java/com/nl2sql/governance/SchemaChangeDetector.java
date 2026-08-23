package com.nl2sql.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Schema 变更检测（REQ-522、D-26/D-27）：定时对比 information_schema 与 schema_fingerprint。
 * 首次运行建指纹不告警；之后不一致则写 schema_changes + 更新指纹。
 */
@Component
public class SchemaChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeDetector.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaChangeDetector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 每小时执行（D-27）。 */
    @Scheduled(cron = "0 0 * * * *")
    public void detect() {
        // 取所有业务表（排除系统表）
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history')",
                String.class);

        for (String table : tables) {
            String newFingerprint = computeFingerprint(table);
            List<String> existing = jdbcTemplate.queryForList(
                    "SELECT fingerprint FROM schema_fingerprint WHERE table_name = ?", String.class, table);

            if (existing.isEmpty()) {
                // 首次：建指纹，不告警（TC-522-01）
                jdbcTemplate.update(
                        "INSERT INTO schema_fingerprint (table_name, fingerprint) VALUES (?, ?)",
                        table, newFingerprint);
            } else if (!existing.get(0).equals(newFingerprint)) {
                // 变更：写变更记录 + 更新指纹
                String old = existing.get(0);
                jdbcTemplate.update(
                        "INSERT INTO schema_changes (change_type, table_name, old_value, new_value) " +
                                "VALUES ('field_modified', ?, ?, ?)",
                        table, old, newFingerprint);
                jdbcTemplate.update(
                        "UPDATE schema_fingerprint SET fingerprint = ?, detected_at = CURRENT_TIMESTAMP WHERE table_name = ?",
                        newFingerprint, table);
                log.warn("schema change detected: table={}", table);
            }
        }
    }

    private String computeFingerprint(String table) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                table);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> c : columns) {
            sb.append(c.get("COLUMN_NAME")).append(':').append(c.get("COLUMN_TYPE")).append(';');
        }
        return md5(sb.toString());
    }

    private String md5(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
