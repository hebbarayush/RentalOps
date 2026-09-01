package com.rentalops.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code paymentStatus} is the effective status: a PENDING charge whose due date has
 * passed is reported as OVERDUE even before the nightly sweep persists that change.
 */
public record RentPaymentResponse(
        Long id,
        Long leaseId,
        Long tenantId,
        Long propertyId,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        LocalDate dueDate,
        LocalDate paidDate,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        String transactionReference,
        String notes
) {
    public static RentPaymentResponse from(RentPayment payment) {
        return new RentPaymentResponse(
                payment.getId(),
                payment.getLease().getId(),
                payment.getTenant().getId(),
                payment.getProperty().getId(),
                payment.getAmountDue(),
                payment.getAmountPaid(),
                payment.getDueDate(),
                payment.getPaidDate(),
                effectiveStatus(payment),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getNotes()
        );
    }

    private static PaymentStatus effectiveStatus(RentPayment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.PENDING
                && payment.getDueDate().isBefore(LocalDate.now())) {
            return PaymentStatus.OVERDUE;
        }
        return payment.getPaymentStatus();
    }
}

