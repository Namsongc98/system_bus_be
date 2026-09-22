package com.ticket_system.manage_revenue_ticket.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface DashboardRecentBookingProjection {
    Long getTicketId();

    String getCustomerName();

    String getRouteName();

    Integer getSeatNumber();

    BigDecimal getAmount();

    LocalDateTime getOccurredAt();
}
