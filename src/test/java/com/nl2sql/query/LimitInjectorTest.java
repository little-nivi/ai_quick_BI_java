package com.nl2sql.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIMIT 注入（REQ-520）。
 * 覆盖 TC-406-03。
 */
class LimitInjectorTest {

    private final LimitInjector injector = new LimitInjector();

    @Test
    void injectsLimitWhenMissing() {
        // TC-406-03
        String result = injector.inject("SELECT SUM(amount) FROM orders");
        assertThat(result).endsWith("LIMIT 1000");
    }

    @Test
    void doesNotInjectWhenLimitExists() {
        String result = injector.inject("SELECT * FROM orders LIMIT 10");
        assertThat(result).isEqualTo("SELECT * FROM orders LIMIT 10");
    }
}
