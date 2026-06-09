package com.ticket_system.manage_revenue_ticket.projection;

public interface CustomerRevenueByRouteProjection {

    Long getUserId();
    String getEmail();
    String getRouteName();
    String getDateArr();
    String getDepartureTime();
    Long getTotalTickets();
    Double getTotalAmount();
}
