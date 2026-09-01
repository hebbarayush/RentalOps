package com.rentalops.maintenance;

/** Optional list filters for maintenance requests. {@code q} matches title / description. */
public record MaintenanceFilter(MaintenanceStatus status, MaintenancePriority priority, String q) {
}
