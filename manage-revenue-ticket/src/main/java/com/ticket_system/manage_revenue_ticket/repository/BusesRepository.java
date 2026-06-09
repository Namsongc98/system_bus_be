package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.projection.CustomerRevenueByRouteProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface BusesRepository extends JpaRepository<Buses, Long> {
    boolean existsByPlateNumber(String plateNumber);

    @Query(value = """
    SELECT b.id AS bus_id,
           b.plate_number,
           COUNT(tk.id) AS total_tickets,
           SUM(tk.price) AS total_amount
    FROM buses b
    JOIN trips t ON t.bus_id = b.id
    JOIN tickets tk ON tk.trip_id = t.id
    WHERE YEAR(t.arrival_time) = :year
      AND MONTH(t.arrival_time) = :month
      AND DAY(t.arrival_time) = :day
      AND (:busId IS NULL OR b.id = :busId)
    GROUP BY b.id, b.plate_number
""", nativeQuery = true)
    List<Map<String, Object>> findBusRevenueNative(
            @Param("year") int year,
            @Param("month") int month,
            @Param("day") int day,
            @Param("busId") Long busId
    );

    @Query(value = """
    SELECT 	u.id AS user_id,
    		u.email,
    		CONCAT(YEAR(tr.arrival_time),"-",MONTH(tr.arrival_time),"-",DAY(tr.arrival_time)) as date_arr,
    		COUNT(t.id) AS total_tickets,
    		SUM(t.price) AS total_amount
    FROM tickets t
	JOIN users u on t.seller_id = u.id
	JOIN trips tr on tr.id = t.id
    WHERE u.role = 'EMPLOYEE'
		AND YEAR(tr.arrival_time) = :year
      	AND MONTH(tr.arrival_time) = :month
     	AND DAY(tr.arrival_time) = :day
     	AND (:userId IS NULL OR u.id = :userId)
    GROUP BY u.id, u.email, CONCAT(YEAR(tr.arrival_time),"-",MONTH(tr.arrival_time),"-",DAY(tr.arrival_time))
""", nativeQuery = true)
    List<Map<String, Object>> findEmployeeRevenueNative(
            @Param("year") int year,
            @Param("month") int month,
            @Param("day") int day,
            @Param("userId") Long userId
    );
    @Query(
            value = """
            SELECT
                u.id AS userId,
                u.email AS email,
                r.route_name AS routeName,
                CONCAT(YEAR(tr.arrival_time), "-", MONTH(tr.arrival_time), "-", DAY(tr.arrival_time)) AS dateArr,
                CONCAT(YEAR(tr.departure_time), "-", MONTH(tr.departure_time), "-", DAY(tr.departure_time)) AS departureTime,
                COUNT(t.id) AS totalTickets,
                SUM(t.price) AS totalAmount
            FROM tickets t
            JOIN users u ON t.customer_id = u.id
            JOIN trips tr ON tr.id = t.id
            JOIN routes r ON tr.route_id = r.id
            WHERE u.role = 'CUSTOMER'
              AND YEAR(tr.arrival_time) = :year
              AND (:month IS NULL OR MONTH(tr.arrival_time) = :month)
              AND (:date IS NULL OR DAY(tr.arrival_time) = :date)
              AND (:userId IS NULL OR u.id = :userId)
            GROUP BY
                u.id,
                u.email,
                r.route_name,
                CONCAT(YEAR(tr.arrival_time), "-", MONTH(tr.arrival_time), "-", DAY(tr.arrival_time)),
                CONCAT(YEAR(tr.departure_time), "-", MONTH(tr.departure_time), "-", DAY(tr.departure_time))
            """,
            nativeQuery = true
    )
    List<CustomerRevenueByRouteProjection> getCustomerRevenueByRouteAndDate(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("date") Integer date,
            @Param("userId") Long userId
    );

}
