package com.rentalops.common.events;

import java.math.BigDecimal;

/**
 * The manager resolved a rent charge the tenant had reported: either confirmed it
 * ({@code confirmed = true}) or dismissed the report ({@code confirmed = false}).
 */
public record RentPaymentSettledEvent(
        Long paymentId,
        Long tenantUserId,
        BigDecimal amount,
        String propertyName,
        boolean confirmed
) {
}
