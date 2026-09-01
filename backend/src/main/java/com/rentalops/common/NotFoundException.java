package com.rentalops.common;

/**
 * Thrown when a requested entity does not exist. Mapped to HTTP 404 by
 * {@link GlobalExceptionHandler}, so callers can distinguish "missing" from
 * "bad input" (400).
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
