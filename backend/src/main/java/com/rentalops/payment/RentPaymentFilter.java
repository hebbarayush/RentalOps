package com.rentalops.payment;

/**
 * Optional list filters for rent payments. {@code q} matches notes / transaction reference;
 * {@code unpaidOnly} keeps only charges that are not fully PAID.
 */
public record RentPaymentFilter(PaymentStatus status, Boolean unpaidOnly, String q) {
}
