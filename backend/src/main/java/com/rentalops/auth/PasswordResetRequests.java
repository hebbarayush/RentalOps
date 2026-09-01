package com.rentalops.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PasswordResetRequests {
    private PasswordResetRequests() {
    }

    public record Forgot(@Email @NotBlank String email) {
    }

    public record Reset(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {
    }
}
