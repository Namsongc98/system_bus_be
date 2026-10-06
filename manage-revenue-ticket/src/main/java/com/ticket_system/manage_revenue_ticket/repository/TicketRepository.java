package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.entity.Ticket;
import com.ticket_system.manage_revenue_ticket.projection.DashboardLoyalCustomerProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardRecentBookingProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardTopRouteProjection;
import com.ticket_system.manage_revenue_ticket.projection.TripTicketStatsProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
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

    // Vé chưa huỷ + doanh thu vé SUCCESS của nhiều chuyến trong 1 query (TripResponse, spec 1.3 S1/D6).
    @Query(value = """
    SELECT trip_id AS tripId,
           COUNT(*) AS ticketCount,
           SUM(CASE WHEN status_ticket <> 'CANCELLED' THEN 1 ELSE 0 END) AS bookedSeats,
           COALESCE(SUM(CASE WHEN status_ticket = 'SUCCESS' THEN price ELSE 0 END), 0) AS revenue
    FROM tickets
    WHERE trip_id IN (:tripIds)
    GROUP BY trip_id
""", nativeQuery = true)
    List<TripTicketStatsProjection> findTripTicketStats(@Param("tripIds") Collection<Long> tripIds);

    boolean existsByTripId(Long tripId);

    // Trip of a ticket without loading the ticket entity — updateTicket locks that trip first.
    @Query("select t.trip.id from Ticket t where t.id = :ticketId")
    Optional<Long> findTripIdById(@Param("ticketId") Long ticketId);

    // Highest seat still held on a trip: a smaller bus must still have that seat (spec 1.3 S4).
    @Query(value = "SELECT MAX(seat_number) FROM tickets WHERE trip_id = :tripId AND status_ticket <> 'CANCELLED'",
            nativeQuery = true)
    Integer findMaxActiveSeatNumberByTripId(@Param("tripId") Long tripId);

    // Smallest capacity a bus may shrink to: the highest seat number / active ticket count held on any of
    // its unfinished trips (B36 b). 0 when nothing is sold. seat_number is nullable (V1) and GREATEST(NULL, n)
    // is NULL in MySQL, so a trip whose tickets have no seat number still counts by its ticket count (L23).
    @Query(value = """
            SELECT COALESCE(MAX(GREATEST(x.max_seat, x.sold)), 0) FROM (
                SELECT COALESCE(MAX(tk.seat_number), 0) AS max_seat, COUNT(*) AS sold
                FROM tickets tk JOIN trips tr ON tr.id = tk.trip_id
                WHERE tr.bus_id = :busId AND tr.status IN ('SCHEDULED', 'ONGOING')
                  AND tk.status_ticket <> 'CANCELLED'
                GROUP BY tk.trip_id
            ) x
            """, nativeQuery = true)
    int findRequiredCapacityForBus(@Param("busId") Long busId);

    boolean existsByCustomerIdAndStatusTicketNot(Long customerId, TicketStatus status);

    // Khách của các vé chưa huỷ — trả trạng thái khách khi huỷ cả chuyến (giống cancelTicket).
    @Query("""
            select distinct t.customer.id from Ticket t
            where t.trip.id = :tripId
              and t.statusTicket <> com.ticket_system.manage_revenue_ticket.Enum.TicketStatus.CANCELLED
              and t.customer is not null
            """)
    List<Long> findActiveCustomerIdsByTripId(@Param("tripId") Long tripId);

    // Huỷ chuyến → huỷ mọi vé chưa huỷ (spec 1.3 D3 = A). Trả số vé đã huỷ. Không clear persistence
    // context: chuyến đang sửa trong cùng transaction phải còn managed; không ai giữ entity Ticket ở đây.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Ticket t set t.statusTicket = com.ticket_system.manage_revenue_ticket.Enum.TicketStatus.CANCELLED
            where t.trip.id = :tripId
              and t.statusTicket <> com.ticket_system.manage_revenue_ticket.Enum.TicketStatus.CANCELLED
            """)
    int cancelActiveTicketsByTripId(@Param("tripId") Long tripId);

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
