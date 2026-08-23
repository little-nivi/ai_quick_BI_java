package com.nl2sql.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存实例配置（REQ-516/517）。
 * semanticCacheService：语义缓存（maximumSize=500，TTL 24h）；
 * permissionCacheService：权限缓存（maximumSize=100，TTL 1h）。
 * 未来切换 Redis 仅需替换这里的 Bean 实现，业务代码不动（ADR D-25）。
 */
@Configuration
public class CacheConfig {

    @Bean(name = "semanticCacheService")
    public CacheService semanticCacheService(
            @Value("${nl2sql.cache.semantic.maximum-size:500}") long maxSize,
            @Value("${nl2sql.cache.semantic.expire-hours:24}") long expireHours) {
        return new CaffeineCacheServiceImpl(maxSize, expireHours);
    }

    @Bean(name = "permissionCacheService")
    public CacheService permissionCacheService(
            @Value("${nl2sql.cache.permission.maximum-size:100}") long maxSize,
            @Value("${nl2sql.cache.permission.expire-hours:1}") long expireHours) {
        return new CaffeineCacheServiceImpl(maxSize, expireHours);
    }
}
