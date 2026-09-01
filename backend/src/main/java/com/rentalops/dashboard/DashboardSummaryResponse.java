package com.rentalops.dashboard;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        long totalProperties,
        long totalUnits,
        long occupiedUnits,
        long vacantUnits,
        long activeTenants,
        long activeLeases,
        long leasesExpiringThisMonth,
        BigDecimal rentExpected,
        BigDecimal rentCollected,
        BigDecimal pendingRent,
        long openMaintenanceRequests
) {
}
