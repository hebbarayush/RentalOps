package com.rentalops.auth;

import com.rentalops.common.BaseEntity;
import com.rentalops.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single-use password-reset token, persisted so resets work across instances and
 * survive a restart. Replaces the former in-memory {@code Map<String, ResetEntry>}.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(User user, String token, Instant expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return !used && expiresAt.isAfter(now);
    }

    public void markUsed() {
        this.used = true;
    }

    public User getUser() { return user; }
    public String getToken() { return token; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
}
