package com.ticket_system.common.exception;

/** Request conflicts with current state (seat taken, trip full). Mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
