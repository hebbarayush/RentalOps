package com.rentalops.auth;

/**
 * Brute-force login guard: blocks an email after too many failed logins within a rolling
 * window. Two implementations back this interface:
 * <ul>
 *   <li>{@link DatabaseLoginAttemptService} (default) — state lives in the {@code login_attempts}
 *       table, so the guard is correct across multiple backend instances.</li>
 *   <li>{@link RedisLoginAttemptService} — same behaviour backed by Redis, used when
 *       {@code app.redis.enabled=true}; avoids a DB round-trip on every login.</li>
 * </ul>
 * {@link InMemoryLoginAttemptService} is kept only as a dependency-free implementation for
 * unit tests; it is not registered as a Spring bean.
 */
public interface LoginAttemptService {
    void assertNotBlocked(String email);

    void recordFailure(String email);

    void reset(String email);
}
