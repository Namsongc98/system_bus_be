package com.ticket_system.manage_revenue_ticket.service;

/**
 * A seat that already had a CANCELLED ticket on this trip was booked again (spec review 0.8, N1).
 * Published inside the booking transaction; handled after commit by {@link StaffNotificationService}.
 */
public record SeatRebookedEvent(Long tripId, Integer seatNumber, Long ticketId, Long customerId) {
}
