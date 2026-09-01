package com.rentalops.maintenance;

import jakarta.validation.constraints.NotNull;

public record MaintenanceUpdateRequest(
        @NotNull MaintenanceStatus status,
        String managerNotes
) {
}

