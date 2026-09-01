package com.rentalops.common.events;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentOverdueEvent(
        Long paymentId, Long tenantUserId, Long managerUserId, BigDecimal amountDue,
        LocalDate dueDate, String propertyName, String tenantName
) {
}
