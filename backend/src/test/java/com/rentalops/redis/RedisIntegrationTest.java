package com.rentalops.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rentalops.auth.LoginAttemptService;
import com.rentalops.auth.RedisLoginAttemptService;
import com.rentalops.auth.TooManyAttemptsException;
import com.rentalops.dashboard.DashboardSummaryResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

/**
 * Proves the optional-Redis path actually works: with {@code app.redis.enabled=true} and
 * {@code spring.cache.type=redis} the brute-force guard is the Redis implementation and the
 * dashboard cache round-trips its {@code record} DTOs as JSON through a real Redis (embedded,
 * so no external server is needed on CI).
 *
 * <p>{@code @DirtiesContext} tears the Redis-wired context (and its Lettuce pool) down at the
 * end of the class; the embedded server is left for the JVM shutdown hook to reap.
 */
@SpringBootTest(properties = {
        "app.redis.enabled=true",
        "spring.cache.type=redis",
        "spring.cache.redis.time-to-live=60s",
        "app.security.login.max-attempts=3",
        "app.security.login.window-minutes=5",
        // own in-memory DB so this context's create-drop teardown can't touch the shared one
        "spring.datasource.url=jdbc:h2:mem:redis-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
@DirtiesContext
class RedisIntegrationTest {

    private static RedisServer redisServer;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        redisServer = new RedisServer(port);
        redisServer.start();
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> port);
    }

    @Autowired LoginAttemptService loginAttemptService;
    @Autowired CacheManager cacheManager;

    @Test
    void bruteForceGuardIsRedisBacked() {
        assertThat(loginAttemptService).isInstanceOf(RedisLoginAttemptService.class);

        String email = "redis-guard-" + System.nanoTime() + "@example.com";
        assertThatCode(() -> loginAttemptService.assertNotBlocked(email)).doesNotThrowAnyException();

        for (int i = 0; i < 3; i++) {
            loginAttemptService.recordFailure(email);
        }
        assertThatThrownBy(() -> loginAttemptService.assertNotBlocked(email))
                .isInstanceOf(TooManyAttemptsException.class);

        loginAttemptService.reset(email);
        assertThatCode(() -> loginAttemptService.assertNotBlocked(email)).doesNotThrowAnyException();
    }

    @Test
    void dashboardCacheRoundTripsThroughRedis() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);

        Cache cache = cacheManager.getCache("dashboardSummary");
        assertThat(cache).isNotNull();

        DashboardSummaryResponse original = new DashboardSummaryResponse(
                3, 15, 1, 14, 2, 1, 0,
                new BigDecimal("190000.00"), new BigDecimal("95000.00"), new BigDecimal("95000.00"), 3);

        cache.put("42", original);

        DashboardSummaryResponse fromRedis = cache.get("42", DashboardSummaryResponse.class);
        assertThat(fromRedis).isEqualTo(original);
    }
}
