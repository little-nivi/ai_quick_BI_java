package com.nl2sql.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源（ADR 方案 A）：
 * - 主数据源 {@code dataSource}（@Primary，读写）：供 Flyway 迁移建表。
 * - 只读数据源 {@code readonlyDataSource}（read-only=true，SR-2）：供 SqlExecutor 查询。
 *
 * 显式定义两个 DataSource，DataSourceAutoConfiguration 因存在 DataSource bean 自动跳过。
 * 密码经环境变量注入，不落盘（章程 §环境策略）。
 */
@Configuration
public class DataSourceConfig {

    /** 主数据源：读写，供 Flyway。Hikari 参数对齐 ADR D-01。 */
    @Primary
    @Bean(name = "dataSource", destroyMethod = "close")
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);          // ADR D-01
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        return new HikariDataSource(config);
    }

    /** 只读数据源：read-only=true（SR-2 只读连接），供 SqlExecutor。 */
    @Bean(name = "readonlyDataSource", destroyMethod = "close")
    public DataSource readonlyDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);               // SR-2：MySQL 连接强制只读
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30_000);
        return new HikariDataSource(config);
    }

    /** 读写 JdbcTemplate（@Primary）：AuthService/AuditService/MetricMatcher 等按类型注入的默认选择。 */
    @Primary
    @Bean(name = "jdbcTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** 只读 JdbcTemplate：SqlExecutor 注入用。 */
    @Bean(name = "readonlyJdbcTemplate")
    public JdbcTemplate readonlyJdbcTemplate(@Qualifier("readonlyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
