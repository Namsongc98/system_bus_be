package com.ticket_system.manage_revenue_ticket.projection;

import java.math.BigDecimal;

/** Per-trip ticket numbers for TripResponse: all tickets, non-cancelled tickets and SUCCESS revenue. */
public interface TripTicketStatsProjection {
    Long getTripId();
    Long getTicketCount();
    Long getBookedSeats();
    BigDecimal getRevenue();
}
