package com.rentalops.common.events;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentChargeGeneratedEvent(
        Long paymentId, Long tenantUserId, BigDecimal amount, LocalDate dueDate, String propertyName
) {
}
