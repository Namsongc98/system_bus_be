package com.ticket_system.common.exception;

/** The account exists and the credentials are valid, but an admin has locked it. Mapped to 403. */
public class AccountLockedException extends RuntimeException {
    public static final String MESSAGE = "Tài khoản đã bị khoá";

    public AccountLockedException() {
        super(MESSAGE);
    }
}
