package com.rentalops.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MarkPaymentRequest(
        @DecimalMin("0.01") BigDecimal amountPaid,
        @NotNull PaymentMethod paymentMethod,
        String transactionReference
) {
}

