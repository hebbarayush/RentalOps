package com.rentalops.lease;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaseRenewalRequest(
        LocalDate startDate,
        @NotNull @Future LocalDate endDate,
        @DecimalMin("0.01") BigDecimal monthlyRent,
        @DecimalMin("0.00") BigDecimal securityDeposit
) {
}
