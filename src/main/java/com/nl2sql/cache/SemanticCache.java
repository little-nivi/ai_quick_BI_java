package com.nl2sql.cache;

import com.nl2sql.auth.UserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 语义缓存（REQ-516、D-21）。
 * key = hash(question + schema_version + role)，含 role 防止跨角色串读（SR-8）。
 */
@Component
public class SemanticCache {

    private static final String SCHEMA_VERSION = "v1"; // orders 表结构版本（Schema 指纹 M5 后动态化）

    private final CacheService cache;
    private final long emptyTtlSeconds;

    public SemanticCache(@Qualifier("semanticCacheService") CacheService cache,
                         @Value("${nl2sql.cache.semantic.empty-ttl-minutes:5}") long emptyTtlMinutes) {
        this.cache = cache;
        this.emptyTtlSeconds = emptyTtlMinutes * 60;
    }

    public Object get(String question) {
        return cache.get(key(question));
    }

    public void put(String question, Object result, boolean isEmpty) {
        long ttl = isEmpty ? emptyTtlSeconds : 24 * 3600;
        cache.put(key(question), result, ttl);
    }

    private String key(String question) {
        String role = UserContext.get() == null ? "anon" : UserContext.get().role();
        String raw = question + "|" + SCHEMA_VERSION + "|" + role;
        return sha256(raw);
    }

    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
