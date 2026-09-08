package com.rentalops.common.events;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A tenant reported paying a rent charge out of band; the manager needs to confirm it. */
public record RentPaymentReportedEvent(
        Long paymentId,
        Long managerUserId,
        BigDecimal amountDue,
        LocalDate dueDate,
        String propertyName,
        String tenantName,
        String method,
        String reference
) {
}
