package com.csri.kg.service.cache;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Cache configuration for CSRI Knowledge Graph Service
 * Implements multi-tier caching strategy for performance optimization
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache names used throughout the application
     */
    public static final String QUERY_RESULTS_CACHE = "queryResults";
    public static final String JUSTIFICATION_CACHE = "justifications";
    public static final String CONCEPT_CACHE = "concepts";
    public static final String ASSERTION_CACHE = "assertions";
    public static final String REASONING_CACHE = "reasoning";
    public static final String ONTOLOGY_CACHE = "ontologies";

    /**
     * Primary cache manager using Caffeine for local caching
     * Optimized for high-performance in-memory operations
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Configure Caffeine with optimized settings
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)  // Max entries per cache
            .expireAfterWrite(30, TimeUnit.MINUTES)  // TTL for write operations
            .expireAfterAccess(15, TimeUnit.MINUTES)  // TTL for read operations
            .recordStats());  // Enable cache statistics for monitoring

        return cacheManager;
    }

    /**
     * Customizer for fine-tuning cache behavior per cache type
     */
    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer() {
        return cacheManager -> {
            // Allow null values to be cached (important for CSNePS queries that may return null)
            cacheManager.setAllowNullValues(true);

            // Configure specific caches with different settings
            cacheManager.setCacheNames(
                Arrays.asList(
                    QUERY_RESULTS_CACHE,
                    JUSTIFICATION_CACHE,
                    CONCEPT_CACHE,
                    ASSERTION_CACHE,
                    REASONING_CACHE,
                    ONTOLOGY_CACHE
                )
            );
        };
    }

    /**
     * Production profile - distributed cache using Redis
     * Configured for production environments with multiple instances
     */
    @Configuration
    @Profile("production")
    static class ProductionCacheConfig {

        // Note: Redis configuration would be added here for production
        // This would require spring-boot-starter-data-redis dependency
        // and Redis server configuration

        /*
        @Bean
        @ConditionalOnClass(RedisConnectionFactory.class)
        public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));

            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
        }
        */
    }

    /**
     * Development profile - simple cache for testing
     */
    @Configuration
    @Profile("development")
    static class DevelopmentCacheConfig {

        @Bean
        public CacheManagerCustomizer<CaffeineCacheManager> devCacheCustomizer() {
            return cacheManager -> {
                // Reduced cache sizes for development
                cacheManager.setCaffeine(Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats());
            };
        }
    }
}
