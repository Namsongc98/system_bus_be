package com.ticket_system.manage_revenue_ticket.projection;

import java.math.BigDecimal;

public interface TopUserProjection {
    Long getUserId();
    String getEmail();
    Long getTotalTickets();
    BigDecimal getTotalAmount();
}
