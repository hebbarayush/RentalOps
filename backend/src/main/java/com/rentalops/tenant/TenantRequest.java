package com.rentalops.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TenantRequest(
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @NotBlank String phone,
        String emergencyContactName,
        String emergencyContactPhone,
        String governmentIdNumber,
        @NotNull TenantStatus status
) {
}

