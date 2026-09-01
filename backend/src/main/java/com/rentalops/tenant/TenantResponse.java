package com.rentalops.tenant;

public record TenantResponse(
        Long id,
        Long managerId,
        Long userId,
        String fullName,
        String email,
        String phone,
        String emergencyContactName,
        String emergencyContactPhone,
        String governmentIdNumber,
        TenantStatus status
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getManager().getId(),
                tenant.getUser() != null ? tenant.getUser().getId() : null,
                tenant.getFullName(),
                tenant.getEmail(),
                tenant.getPhone(),
                tenant.getEmergencyContactName(),
                tenant.getEmergencyContactPhone(),
                tenant.getGovernmentIdNumber(),
                tenant.getStatus()
        );
    }
}
