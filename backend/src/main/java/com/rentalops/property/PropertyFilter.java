package com.rentalops.property;

/** Optional list filters for properties. {@code q} matches name / address / city. */
public record PropertyFilter(String city, PropertyStatus status, PropertyType type, String q) {
}
