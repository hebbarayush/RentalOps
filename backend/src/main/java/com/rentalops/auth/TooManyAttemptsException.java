package com.rentalops.auth;

/** Thrown by {@link LoginAttemptService}; mapped to HTTP 429. */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException(String message) {
        super(message);
    }
}
