package com.rentalops.common.events;

public record LeaseExpiredEvent(
        Long leaseId, Long managerUserId, String unitNumber, String propertyName, String tenantName
) {
}
