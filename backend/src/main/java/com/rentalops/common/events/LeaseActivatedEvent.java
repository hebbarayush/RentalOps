package com.rentalops.common.events;

import java.time.LocalDate;

public record LeaseActivatedEvent(
        Long leaseId, Long tenantUserId, String unitNumber, String propertyName,
        LocalDate startDate, LocalDate endDate
) {
}
