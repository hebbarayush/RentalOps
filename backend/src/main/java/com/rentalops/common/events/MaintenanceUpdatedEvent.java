package com.rentalops.common.events;

public record MaintenanceUpdatedEvent(
        Long requestId, Long tenantUserId, String title, String status
) {
}
