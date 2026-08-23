package com.nl2sql.cache;

/**
 * 缓存抽象层接口（REQ-516/517、ADR D-25）。
 * 通用 get/put/evict/clear + 带 TTL；具体缓存用不同实例区分，不在接口硬编码缓存类型。
 * 设计目标：当下用 Caffeine（零运维），未来可平滑切换到 Redis（实现 RedisCacheServiceImpl）。
 */
public interface CacheService {

    /** 获取缓存值；未命中返回 null。 */
    Object get(String key);

    /** 写入缓存，TTL 单位秒。 */
    void put(String key, Object value, long ttlSeconds);

    /** 删除指定 key。 */
    void evict(String key);

    /** 清空所有缓存。 */
    void clear();
}
