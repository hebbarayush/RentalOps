package com.rentalops.auth;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed {@link LoginAttemptService}, used when {@code app.redis.enabled=true}. Same
 * semantics as {@link DatabaseLoginAttemptService} but avoids a DB round-trip on every login
 * attempt; the counter expires on its own via Redis TTL instead of needing a sweep.
 */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisLoginAttemptService implements LoginAttemptService {
    private static final String COUNT_PREFIX = "login_attempts:count:";
    private static final String BLOCK_PREFIX = "login_attempts:blocked:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration window;

    public RedisLoginAttemptService(
            StringRedisTemplate redis,
            @Value("${app.security.login.max-attempts:10}") int maxAttempts,
            @Value("${app.security.login.window-minutes:15}") int windowMinutes
    ) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    public void assertNotBlocked(String email) {
        if (redis.hasKey(BLOCK_PREFIX + key(email))) {
            throw new TooManyAttemptsException(
                    "Too many failed sign-in attempts. Try again in a few minutes.");
        }
    }

    @Override
    public void recordFailure(String email) {
        String countKey = COUNT_PREFIX + key(email);
        Long count = redis.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redis.expire(countKey, window.toMinutes(), TimeUnit.MINUTES);
        }
        if (count != null && count >= maxAttempts) {
            redis.opsForValue().set(BLOCK_PREFIX + key(email), "1", window);
        }
    }

    @Override
    public void reset(String email) {
        redis.delete(COUNT_PREFIX + key(email));
        redis.delete(BLOCK_PREFIX + key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.toLowerCase();
    }
}
