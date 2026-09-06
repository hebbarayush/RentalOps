package com.rentalops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.dashboard.DashboardSummaryResponse;
import com.rentalops.dashboard.DashboardTrendsResponse;
import com.rentalops.dashboard.RentAtRiskResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cache serialization for the Redis-backed cache. Only active when {@code spring.cache.type=redis}
 * (set {@code SPRING_CACHE_TYPE=redis} alongside {@code REDIS_ENABLED=true}); the default
 * Caffeine path needs none of this.
 *
 * <p>Without it, Spring's auto-configured {@code RedisCacheManager} serializes values with JDK
 * serialization &mdash; which would force every cached DTO to implement {@link java.io.Serializable}
 * and stores opaque binary blobs. Each dashboard cache holds exactly one response type, so we bind
 * a typed JSON serializer per cache: the values round-trip our {@code record} DTOs with no extra
 * annotations and stay readable from {@code redis-cli}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheCustomizer(
            ObjectMapper objectMapper,
            @Value("${spring.cache.redis.time-to-live:60s}") Duration ttl) {
        return builder -> builder
                .withCacheConfiguration("dashboardSummary", jsonCache(ttl, objectMapper, DashboardSummaryResponse.class))
                .withCacheConfiguration("dashboardTrends", jsonCache(ttl, objectMapper, DashboardTrendsResponse.class))
                .withCacheConfiguration("rentAtRisk", jsonCache(ttl, objectMapper, RentAtRiskResponse.class));
    }

    private static RedisCacheConfiguration jsonCache(Duration ttl, ObjectMapper mapper, Class<?> type) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("rentalops:cache:")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, type)));
    }
}
