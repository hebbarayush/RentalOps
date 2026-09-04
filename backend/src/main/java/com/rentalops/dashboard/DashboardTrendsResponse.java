package com.rentalops.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Six months of movement behind the dashboard's current-snapshot figures. Each series is
 * oldest-first and always has one entry per calendar month in the window, zero-filled where
 * there is no data, so the frontend can render a fixed-width strip without gap handling.
 *
 * <p>Occupancy history is not stored anywhere; {@code unitsUnderLease} is derived from lease
 * date ranges and is a fair proxy, not a point-in-time occupancy record.
 */
public record DashboardTrendsResponse(
        List<CollectionPoint> collection,
        List<OccupancyPoint> occupancy,
        List<MaintenancePoint> maintenance
) {
    /** month is "yyyy-MM". */
    public record CollectionPoint(String month, BigDecimal expected, BigDecimal collected) {
    }

    public record OccupancyPoint(String month, long unitsUnderLease, long totalUnits) {
    }

    public record MaintenancePoint(String month, long opened, long resolved) {
    }
}
