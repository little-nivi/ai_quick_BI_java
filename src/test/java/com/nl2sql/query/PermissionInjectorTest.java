package com.nl2sql.query;

import com.nl2sql.auth.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限注入（REQ-507、D-12、SR-4）。
 * 覆盖 TC-507-01（operator 注入 region）、admin/all 不注入。
 */
class PermissionInjectorTest {

    private final PermissionInjector injector = new PermissionInjector();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void operatorInjectsRegion() {
        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东,华南"));

        String result = injector.inject("SELECT SUM(amount) FROM orders");

        assertThat(result).contains("region IN ('华东','华南')");
    }

    @Test
    void allScopeDoesNotInject() {
        UserContext.set(new UserContext.AuthUser(1L, "admin", "all"));

        String result = injector.inject("SELECT SUM(amount) FROM orders");

        assertThat(result).isEqualTo("SELECT SUM(amount) FROM orders");
    }

    @Test
    void nullUserDoesNotInject() {
        UserContext.clear();
        String result = injector.inject("SELECT SUM(amount) FROM orders");
        assertThat(result).isEqualTo("SELECT SUM(amount) FROM orders");
    }

    @Test
    void injectsBeforeGroupBy() {
        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东"));

        String result = injector.inject("SELECT region, COUNT(*) FROM orders GROUP BY region");

        assertThat(result).contains("WHERE region IN ('华东')");
        assertThat(result).contains("GROUP BY region");
        assertThat(result.indexOf("WHERE")).isLessThan(result.indexOf("GROUP BY"));
    }

    @Test
    void injectsBeforeOrderBy() {
        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东"));

        String result = injector.inject("SELECT region, COUNT(*) FROM orders ORDER BY region");

        assertThat(result).contains("WHERE region IN ('华东')");
        assertThat(result).contains("ORDER BY region");
    }

    @Test
    void existingWhereGetsAndClause() {
        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东"));

        String result = injector.inject("SELECT SUM(amount) FROM orders WHERE status = 'completed'");

        assertThat(result).contains("WHERE status = 'completed' AND region IN ('华东')");
    }
}
