package com.rentalops.lease;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaseResponse(
        Long id,
        Long propertyId,
        Long tenantId,
        String unitNumber,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        BigDecimal securityDeposit,
        LeaseStatus leaseStatus,
        String agreementFileUrl,
        int billingDayOfMonth,
        LocalDate nextChargeDate,
        Long renewedFromLeaseId
) {
    public static LeaseResponse from(Lease lease) {
        return new LeaseResponse(
                lease.getId(),
                lease.getProperty().getId(),
                lease.getTenant().getId(),
                lease.getUnitNumber(),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getMonthlyRent(),
                lease.getSecurityDeposit(),
                lease.getLeaseStatus(),
                lease.getAgreementFileUrl(),
                lease.getBillingDayOfMonth(),
                lease.getNextChargeDate(),
                lease.getRenewedFromLeaseId()
        );
    }
}
