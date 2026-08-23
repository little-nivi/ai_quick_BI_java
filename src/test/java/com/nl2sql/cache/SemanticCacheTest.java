package com.nl2sql.cache;

import com.nl2sql.auth.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语义缓存（REQ-516、D-21）。
 * 覆盖 TC-406-01（命中）、TC-406-02（跨角色隔离）。
 */
class SemanticCacheTest {

    private final SemanticCache cache = new SemanticCache(new CaffeineCacheServiceImpl(100, 24), 5);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void cacheHitReturnsStoredValue() {
        // TC-406-01 命中
        UserContext.set(new UserContext.AuthUser(1L, "admin", "all"));
        cache.put("总销售额", "result-1", false);

        Object hit = cache.get("总销售额");
        assertThat(hit).isEqualTo("result-1");
    }

    @Test
    void differentRolesDoNotShareCache() {
        // TC-406-02 跨角色隔离：key 含 role
        UserContext.set(new UserContext.AuthUser(1L, "admin", "all"));
        cache.put("总销售额", "admin-result", false);

        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东"));
        Object operatorHit = cache.get("总销售额");

        assertThat(operatorHit).isNull();
    }
}
