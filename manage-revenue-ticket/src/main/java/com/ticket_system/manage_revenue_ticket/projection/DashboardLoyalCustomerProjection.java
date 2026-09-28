package com.ticket_system.manage_revenue_ticket.projection;

import java.math.BigDecimal;

public interface DashboardLoyalCustomerProjection {
    Long getCustomerId();

    String getCustomerName();

    Long getTrips();

    BigDecimal getTotalSpent();
}
