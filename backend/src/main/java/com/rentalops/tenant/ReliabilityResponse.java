package com.rentalops.tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A tenant's rent-payment reliability, derived from their charge history using a
 * recency-weighted score (see {@link TenantReliabilityService}). {@code score} is 0-100;
 * {@code band} buckets it; {@code reasons} explains what drove the score; {@code predictedLateRisk}
 * flags tenants likely to miss the next payment.
 */
public record ReliabilityResponse(
        Long tenantId,
        String tenantName,
        int totalCharges,
        int onTimeCount,
        int lateCount,
        int currentlyOverdueCount,
        double avgDaysLate,
        int score,
        String band,
        boolean predictedLateRisk,
        List<String> reasons,
        BigDecimal outstanding,
        LocalDate nextDueDate
) {
}
