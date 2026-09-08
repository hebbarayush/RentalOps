package com.rentalops.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A tenant reporting that they have paid a rent charge outside the app. The charge is not
 * marked PAID by this — it waits for the manager to confirm.
 */
public record ReportPaymentRequest(
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 255) String transactionReference,
        @Size(max = 500) String note
) {
}
