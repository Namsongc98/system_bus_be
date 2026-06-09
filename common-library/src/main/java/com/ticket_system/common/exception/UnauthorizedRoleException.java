package com.ticket_system.common.exception;

public class UnauthorizedRoleException extends RuntimeException {
    public UnauthorizedRoleException(String message) {
        super(message);
    }
}
