package com.example.cachefileapi.config;

/**
 * Central registry of cache name constants to avoid magic strings scattered across the codebase.
 *
 * <p>These names must match the keys registered in
 * {@link RedisConfig#cacheManager(org.springframework.data.redis.connection.RedisConnectionFactory,
 * org.springframework.data.redis.cache.RedisCacheConfiguration)}.</p>
 */
public final class CacheConstants {

    /** Cache for individual {@link com.example.cachefileapi.entity.Product} lookups by id. */
    public static final String PRODUCTS = "products";

    /** Cache for paginated / full product list responses. */
    public static final String PRODUCTS_LIST = "products-list";

    private CacheConstants() {
        // utility class — do not instantiate
    }
}
