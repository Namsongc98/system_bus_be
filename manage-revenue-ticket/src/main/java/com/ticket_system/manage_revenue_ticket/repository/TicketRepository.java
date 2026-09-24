package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Ticket;
import com.ticket_system.manage_revenue_ticket.projection.DashboardLoyalCustomerProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardRecentBookingProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardTopRouteProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Vé chưa huỷ của 1 chuyến — dùng để so với sức chứa xe.
    @Query(value = """
    SELECT COUNT(*)
    FROM tickets
    WHERE trip_id = :tripId
      AND status_ticket <> 'CANCELLED'
""", nativeQuery = true)
    long countActiveTicketsByTripId(@Param("tripId") Long tripId);

    // Ghế đang có vé chưa huỷ (bỏ qua vé excludeTicketId khi sửa vé; null khi tạo).
    @Query(value = """
    SELECT COUNT(*)
    FROM tickets
    WHERE trip_id = :tripId
      AND seat_number = :seatNumber
      AND status_ticket <> 'CANCELLED'
      AND (:excludeTicketId IS NULL OR id <> :excludeTicketId)
""", nativeQuery = true)
    long countActiveSeat(@Param("tripId") Long tripId,
                         @Param("seatNumber") Integer seatNumber,
                         @Param("excludeTicketId") Long excludeTicketId);

    default boolean existsActiveSeat(Long tripId, Integer seatNumber, Long excludeTicketId) {
        return countActiveSeat(tripId, seatNumber, excludeTicketId) > 0;
    }

    // Ghế từng có vé bị huỷ trên chuyến này (N1: báo ADMIN khi ghế được đặt lại).
    @Query(value = """
    SELECT COUNT(*)
    FROM tickets
    WHERE trip_id = :tripId
      AND seat_number = :seatNumber
      AND status_ticket = 'CANCELLED'
""", nativeQuery = true)
    long countCancelledSeat(@Param("tripId") Long tripId, @Param("seatNumber") Integer seatNumber);

    default boolean existsCancelledSeat(Long tripId, Integer seatNumber) {
        return countCancelledSeat(tripId, seatNumber) > 0;
    }

    @Query(value = """
        SELECT
            b.id AS busId,
            b.plate_number AS plateNumber,
            u_seller.email AS seller,
            u_driver.email AS driver,
            COUNT(tk.id) AS totalTicket,
            SUM(tk.price) AS totalPrice
        FROM tickets tk
        JOIN trips tr ON tk.trip_id = tr.id
        JOIN buses b ON tr.bus_id = b.id
        LEFT JOIN users u_seller ON tk.seller_id = u_seller.id
        LEFT JOIN users u_driver ON tr.driver_id = u_driver.id
        WHERE (:busId IS NULL OR b.id = :busId)
          AND tk.issued_at BETWEEN :fromDate AND :toDate
        GROUP BY
            b.id, b.plate_number, u_seller.email, u_driver.email
        ORDER BY
            b.id, u_seller.email
    """, nativeQuery = true)
    List<Map<String, Object>> getTicketSummaryByBusAndTime(
            @Param("busId") Long busId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    @Query("""
            select count(t.id) as total
            from Ticket t where t.seller.id = :sellerId
            	and MONTH(t.issuedAt) = :month
            	AND YEAR(t.issuedAt) = :year
            	and t.userStatus = 'BOOKED'
            group by seller.id
            """)
        Long countCompletedTripsBySeller(@Param("month") byte month, @Param("year") short year,@Param("sellerId") Long sellerId);

    @Query("""
            SELECT SUM(COALESCE(t.price,0))
            FROM Ticket t
            WHERE t.statusTicket = com.ticket_system.manage_revenue_ticket.Enum.TicketStatus.SUCCESS
              AND t.issuedAt >= :start
              AND t.issuedAt < :end
            """)
    BigDecimal sumSuccessfulTicketRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(t.id)
            FROM Ticket t
            WHERE t.statusTicket = com.ticket_system.manage_revenue_ticket.Enum.TicketStatus.SUCCESS
              AND t.issuedAt >= :start
              AND t.issuedAt < :end
            """)
    long countSuccessfulTickets(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(DISTINCT t.customer.id)
            FROM Ticket t
            WHERE t.statusTicket = com.ticket_system.manage_revenue_ticket.Enum.TicketStatus.SUCCESS
              AND t.customer IS NOT NULL
              AND t.issuedAt >= :start
              AND t.issuedAt < :end
            """)
    long countActiveCustomers(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT
                r.id AS routeId,
                r.route_name AS routeName,
                COUNT(t.id) AS ticketsSold
            FROM tickets t
            JOIN trips tr ON tr.id = t.trip_id
            JOIN routes r ON r.id = tr.route_id
            WHERE t.status_ticket = 'SUCCESS'
              AND t.issued_at >= :start
              AND t.issued_at < :end
            GROUP BY r.id, r.route_name
            ORDER BY COUNT(t.id) DESC, r.id ASC
            """, nativeQuery = true)
    List<DashboardTopRouteProjection> findTopRoutes(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query(value = """
            SELECT
                u.id AS customerId,
                COALESCE(p.full_name, u.email) AS customerName,
                COUNT(DISTINCT t.trip_id) AS trips,
                COALESCE(SUM(t.price), 0) AS totalSpent
            FROM tickets t
            JOIN users u ON u.id = t.customer_id
            LEFT JOIN profiles p ON p.user_id = u.id
            WHERE t.status_ticket = 'SUCCESS'
              AND t.issued_at >= :start
              AND t.issued_at < :end
            GROUP BY u.id, p.full_name, u.email
            ORDER BY SUM(t.price) DESC, COUNT(t.id) DESC, u.id ASC
            """, nativeQuery = true)
    List<DashboardLoyalCustomerProjection> findLoyalCustomers(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query(value = """
            SELECT
                t.id AS ticketId,
                COALESCE(p.full_name, u.email) AS customerName,
                r.route_name AS routeName,
                t.seat_number AS seatNumber,
                t.price AS amount,
                t.issued_at AS occurredAt
            FROM tickets t
            JOIN trips tr ON tr.id = t.trip_id
            JOIN routes r ON r.id = tr.route_id
            LEFT JOIN users u ON u.id = t.customer_id
            LEFT JOIN profiles p ON p.user_id = u.id
            WHERE t.status_ticket = 'SUCCESS'
            ORDER BY t.issued_at DESC, t.id DESC
            """, nativeQuery = true)
    List<DashboardRecentBookingProjection> findRecentSuccessfulBookings(Pageable pageable);

    @Query(value = """
            SELECT
                r.id AS routeId,
                r.route_name AS routeName,
                COALESCE(SUM(t.price), 0) AS totalRevenue,
                COUNT(t.id) AS ticketsSold
            FROM tickets t
            JOIN trips tr ON tr.id = t.trip_id
            JOIN routes r ON r.id = tr.route_id
            WHERE t.status_ticket = 'SUCCESS'
              AND t.issued_at >= :start
              AND t.issued_at < :end
            GROUP BY r.id, r.route_name
            ORDER BY SUM(t.price) DESC, r.id ASC
            """, nativeQuery = true)
    List<Map<String, Object>> findRevenueByRoute(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT
                DATE(t.issued_at) AS reportDate,
                COALESCE(SUM(t.price), 0) AS totalRevenue
            FROM tickets t
            WHERE t.status_ticket = 'SUCCESS'
              AND t.issued_at >= :start
              AND t.issued_at < :end
            GROUP BY DATE(t.issued_at)
            ORDER BY reportDate ASC
            """, nativeQuery = true)
    List<Map<String, Object>> findDailyRevenueDensity(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
