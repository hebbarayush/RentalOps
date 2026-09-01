package com.rentalops.auth;

import com.rentalops.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Per-email failed-login counter, persisted so the brute-force guard works across instances.
 * Replaces the former in-memory {@code Map<String, Deque<Instant>>}.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant windowStart;

    private Instant blockedUntil;

    protected LoginAttempt() {
    }

    public LoginAttempt(String email) {
        this.email = email;
        this.attemptCount = 0;
        this.windowStart = Instant.now();
    }

    public boolean isBlocked(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    /** Record one more failure; block once the count reaches {@code maxAttempts} within the window. */
    public void registerFailure(int maxAttempts, Duration window, Instant now) {
        if (windowStart.plus(window).isBefore(now)) {
            windowStart = now;
            attemptCount = 0;
            blockedUntil = null;
        }
        attemptCount++;
        if (attemptCount >= maxAttempts) {
            blockedUntil = now.plus(window);
        }
    }

    public void clear() {
        attemptCount = 0;
        windowStart = Instant.now();
        blockedUntil = null;
    }

    public String getEmail() { return email; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getBlockedUntil() { return blockedUntil; }
}
