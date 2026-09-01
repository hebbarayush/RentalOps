package com.rentalops.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link LoginAttemptService}: state lives in the {@code login_attempts} table, so the
 * guard is correct across multiple backend instances and survives a restart.
 */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class DatabaseLoginAttemptService implements LoginAttemptService {
    private final LoginAttemptRepository repository;
    private final int maxAttempts;
    private final Duration window;

    public DatabaseLoginAttemptService(
            LoginAttemptRepository repository,
            @Value("${app.security.login.max-attempts:10}") int maxAttempts,
            @Value("${app.security.login.window-minutes:15}") int windowMinutes
    ) {
        this.repository = repository;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertNotBlocked(String email) {
        repository.findByEmail(key(email))
                .filter(a -> a.isBlocked(Instant.now()))
                .ifPresent(a -> {
                    throw new TooManyAttemptsException(
                            "Too many failed sign-in attempts. Try again in a few minutes.");
                });
    }

    @Override
    @Transactional
    public void recordFailure(String email) {
        String normalized = key(email);
        LoginAttempt attempt = repository.findByEmail(normalized)
                .orElseGet(() -> new LoginAttempt(normalized));
        attempt.registerFailure(maxAttempts, window, Instant.now());
        repository.save(attempt);
    }

    @Override
    @Transactional
    public void reset(String email) {
        repository.findByEmail(key(email)).ifPresent(LoginAttempt::clear);
    }

    private static String key(String email) {
        return email == null ? "" : email.toLowerCase();
    }
}
