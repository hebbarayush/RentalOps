package com.rentalops.common.events;

public record MaintenanceCreatedEvent(
        Long requestId, Long managerUserId, String title, String propertyName,
        String category, String priority
) {
}
