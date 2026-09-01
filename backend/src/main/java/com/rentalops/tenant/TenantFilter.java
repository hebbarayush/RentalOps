package com.rentalops.tenant;

/** Optional list filters for tenants. {@code q} matches name / email / phone. */
public record TenantFilter(TenantStatus status, String q) {
}
