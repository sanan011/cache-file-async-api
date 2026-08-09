package com.example.cachefileapi.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis & Cache configuration.
 *
 * <ul>
 *   <li>Uses {@link GenericJackson2JsonRedisSerializer} so cache entries are
 *       human-readable JSON rather than binary Java serialization.</li>
 *   <li>Provides a typed {@link RedisTemplate} for direct Redis operations.</li>
 *   <li>Defines per-cache TTL overrides via
 *       {@link RedisCacheManager.RedisCacheManagerBuilder#withCacheConfiguration}.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /** Default cache TTL (10 minutes). Overridden per cache where needed. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * Default {@link RedisCacheConfiguration} shared across all caches unless overridden.
     */
    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    /**
     * {@link CacheManager} wired to Redis with per-cache TTL overrides.
     *
     * <p>Cache name constants live in {@link com.example.cachefileapi.config.CacheConstants}.</p>
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     RedisCacheConfiguration defaultCacheConfiguration) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfiguration)
                // Per-cache TTL overrides
                .withCacheConfiguration(
                        CacheConstants.PRODUCTS,
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(15)))
                .withCacheConfiguration(
                        CacheConstants.PRODUCTS_LIST,
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(5)))
                .transactionAware()
                .build();
    }

    /**
     * A generic {@link RedisTemplate} for direct key/value operations
     * (not required for cache abstraction but useful for manual cache control).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
