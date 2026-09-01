package com.rentalops.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RentPaymentRequest(
        @NotNull Long leaseId,
        @DecimalMin("0.01") BigDecimal amountDue,
        @NotNull LocalDate dueDate,
        String notes
) {
}

