package com.ticket_system.manage_revenue_ticket.Dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueTrendResponse(
        String label,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal amount
) {
}
