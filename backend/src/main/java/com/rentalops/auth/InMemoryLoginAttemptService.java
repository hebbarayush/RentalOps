package com.rentalops.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency-free in-memory implementation, kept for unit tests and as a reference
 * implementation. Not a Spring bean — production wiring uses {@link DatabaseLoginAttemptService}
 * or {@link RedisLoginAttemptService} so state survives a restart and is shared across instances.
 */
public class InMemoryLoginAttemptService implements LoginAttemptService {
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public InMemoryLoginAttemptService(int maxAttempts, int windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    public void assertNotBlocked(String email) {
        Deque<Instant> attempts = failures.get(key(email));
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= maxAttempts) {
                throw new TooManyAttemptsException(
                        "Too many failed sign-in attempts. Try again in a few minutes.");
            }
        }
    }

    @Override
    public void recordFailure(String email) {
        Deque<Instant> attempts = failures.computeIfAbsent(key(email), k -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.addLast(Instant.now());
            prune(attempts);
        }
    }

    @Override
    public void reset(String email) {
        failures.remove(key(email));
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    private static String key(String email) {
        return email == null ? "" : email.toLowerCase();
    }
}
