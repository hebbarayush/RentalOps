package com.rentalops.common.events;

import java.time.LocalDate;

public record LeaseExpiringEvent(
        Long leaseId, Long managerUserId, String unitNumber, String propertyName,
        LocalDate endDate, long daysLeft
) {
}
