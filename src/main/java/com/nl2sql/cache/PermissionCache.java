package com.nl2sql.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 权限缓存（REQ-517）：role → data_scope，TTL 1h，变更频率极低。
 */
@Component
public class PermissionCache {

    private final CacheService cache;

    public PermissionCache(@Qualifier("permissionCacheService") CacheService cache) {
        this.cache = cache;
    }

    public String get(String role) {
        Object v = cache.get("role:" + role);
        return v == null ? null : v.toString();
    }

    public void put(String role, String dataScope) {
        cache.put("role:" + role, dataScope, 3600);
    }
}
