package com.rentalops.common.outbox;

public enum OutboxStatus {
    /** Written, not yet delivered. Picked up by the processor. */
    PENDING,
    /** Delivered successfully. Terminal. */
    PROCESSED,
    /** Delivery failed too many times. Terminal — needs a human / a manual replay. */
    FAILED
}
