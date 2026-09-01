package com.rentalops.property;

public record PropertyResponse(
        Long id,
        Long managerId,
        String name,
        String description,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        PropertyType propertyType,
        int totalUnits,
        int occupiedUnits,
        PropertyStatus status
) {
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getManager().getId(),
                property.getName(),
                property.getDescription(),
                property.getAddressLine1(),
                property.getAddressLine2(),
                property.getCity(),
                property.getState(),
                property.getPostalCode(),
                property.getCountry(),
                property.getPropertyType(),
                property.getTotalUnits(),
                property.getOccupiedUnits(),
                property.getStatus()
        );
    }
}

