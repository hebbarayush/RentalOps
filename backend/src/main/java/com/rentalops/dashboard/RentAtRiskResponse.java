package com.rentalops.dashboard;

import com.rentalops.tenant.ReliabilityResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * Forward-looking rent-collection risk: tenants flagged as likely to pay late, plus the total
 * outstanding balance that exposure represents.
 */
public record RentAtRiskResponse(
        int tenantsAtRisk,
        BigDecimal exposureAmount,
        List<ReliabilityResponse> tenants
) {
}
