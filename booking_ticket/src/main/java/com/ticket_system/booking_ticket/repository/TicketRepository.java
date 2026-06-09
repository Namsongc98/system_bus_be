package com.ticket_system.booking_ticket.repository;

import com.ticket_system.booking_ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query(value = """
    SELECT COUNT(tk.id)
    FROM tickets tk
    JOIN trips tr ON tk.trip_id = tr.id
    WHERE tr.bus_id = :busId
""", nativeQuery = true)
    int countTicketsByBusId(@Param("busId") Long busId);

    @Query(value = """
    SELECT COUNT(tk.id)
    FROM tickets tk
    JOIN trips tr ON tk.trip_id = tr.id
    WHERE tr.bus_id = :busId
""", nativeQuery = true)
    int countTicketFollowBus(@Param("busId") Long busId);

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

}
