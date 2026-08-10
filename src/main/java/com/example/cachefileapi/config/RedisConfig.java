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

@Configuration
@EnableCaching
public class RedisConfig {

        private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

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
