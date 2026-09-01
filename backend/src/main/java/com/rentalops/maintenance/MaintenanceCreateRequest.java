package com.rentalops.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MaintenanceCreateRequest(
        @NotNull Long tenantId,
        @NotNull Long propertyId,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull MaintenancePriority priority
) {
}

