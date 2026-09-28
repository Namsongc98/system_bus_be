package com.ticket_system.manage_revenue_ticket.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDashboardPeriodException extends IllegalArgumentException {
    public InvalidDashboardPeriodException() {
        super("period must be either weekly or monthly");
    }
}
