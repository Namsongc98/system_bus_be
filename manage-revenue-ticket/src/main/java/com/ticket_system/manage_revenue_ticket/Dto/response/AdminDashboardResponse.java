package com.ticket_system.manage_revenue_ticket.Dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        Period period,
        Kpis kpis,
        List<TopRoute> topRoutes,
        List<LoyalCustomer> loyalCustomers,
        List<RecentBooking> recentBookings
) {
    public record Period(LocalDate startDate, LocalDate endDate) {
    }

    public record Kpis(
            BigDecimal totalRevenue,
            BigDecimal revenueTrendPercent,
            long tripsCompleted,
            BigDecimal tripsTrendPercent,
            long ticketsSold,
            BigDecimal ticketsTrendPercent,
            long activeCustomers,
            BigDecimal customersTrendPercent
    ) {
    }

    public record TopRoute(Long routeId, String name, long ticketsSold, BigDecimal sharePercent) {
    }

    public record LoyalCustomer(
            Long customerId,
            String customer,
            long trips,
            BigDecimal totalSpent,
            int rank
    ) {
    }

    public record RecentBooking(
            Long ticketId,
            String customer,
            String routeName,
            Integer seatNumber,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
    }
}
