package com.nl2sql.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时预置 4 个系统账号（admin/operator/product/manager），ADR D-07。
 * 幂等：仅当 users 表无系统账号时创建。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_system_account = true", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        seed("admin", "admin123", "admin", "all");
        seed("operator", "operator123", "operator", "华东,华南");
        seed("product", "product123", "product", "all");
        seed("manager", "manager123", "manager", "all");
    }

    private void seed(String username, String rawPassword, String role, String dataScope) {
        jdbcTemplate.update(
                "INSERT INTO users (username, role, data_scope, password_hash, is_system_account) " +
                        "VALUES (?, ?, ?, ?, true)",
                username, role, dataScope, encoder.encode(rawPassword));
    }
}
