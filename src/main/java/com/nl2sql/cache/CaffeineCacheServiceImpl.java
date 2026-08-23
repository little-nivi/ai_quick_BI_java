package com.nl2sql.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Caffeine 本地缓存实现（REQ-516/517，默认实现）。
 * 缓存操作 try-catch：写入失败不抛异常，仅打 WARN（降级为不缓存，不影响核心业务）。
 */
public class CaffeineCacheServiceImpl implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCacheServiceImpl.class);

    private final Cache<String, Object> cache;

    public CaffeineCacheServiceImpl(long maximumSize, long expireHours) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofHours(expireHours))
                .recordStats() // REQ-521 命中率监控
                .build();
    }

    @Override
    public Object get(String key) {
        try {
            return cache.getIfPresent(key);
        } catch (Exception e) {
            log.warn("cache get failed, key={}", key, e);
            return null;
        }
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        try {
            cache.put(key, value);
        } catch (Exception e) {
            log.warn("cache put failed, key={}", key, e);
        }
    }

    @Override
    public void evict(String key) {
        try {
            cache.invalidate(key);
        } catch (Exception e) {
            log.warn("cache evict failed, key={}", key, e);
        }
    }

    @Override
    public void clear() {
        try {
            cache.invalidateAll();
        } catch (Exception e) {
            log.warn("cache clear failed", e);
        }
    }

    /** 命中率监控（REQ-521）。 */
    public double hitRate() {
        return cache.stats().hitRate();
    }

    /** 容量使用率（REQ-521）。 */
    public double estimatedSizeRatio() {
        long max = cache.policy().eviction().map(p -> p.getMaximum()).orElse(0L);
        return max == 0 ? 0 : (double) cache.estimatedSize() / max;
    }
}
