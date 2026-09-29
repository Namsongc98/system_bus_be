package com.ticket_system.manage_revenue_ticket.Enum;

/**
 * AVAILABLE: free to be assigned to a trip. IN_USE: set by the system while the bus
 * runs a trip, never by an admin. MAINTENANCE: out of service.
 */
public enum BusStatus {
    AVAILABLE,
    IN_USE,
    MAINTENANCE
}
