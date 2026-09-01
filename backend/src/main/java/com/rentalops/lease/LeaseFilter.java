package com.rentalops.lease;

/** Optional list filters for leases. {@code q} matches the unit number. */
public record LeaseFilter(LeaseStatus status, String q) {
}
