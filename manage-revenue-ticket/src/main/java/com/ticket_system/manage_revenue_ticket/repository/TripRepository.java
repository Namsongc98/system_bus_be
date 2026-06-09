package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @Query(value = """
        SELECT\s
            b.id AS busId,\s
            b.plate_number AS plateNumber,\s
            b.capacity AS capacity,\s
            b.status AS busStatus,\s
            t.revenue AS revenue,\s
            t.departure_time AS departureTime,\s
            t.arrival_time AS arrivalTime,
            r.route_name AS routeName,\s
            r.start_point AS startPoint,\s
            r.end_point AS endPoint,\s
            r.distance_km AS km
        FROM buses b
        JOIN trips t ON t.bus_id = b.id
        JOIN routes r ON t.route_id = r.id
        WHERE r.status = 'ACTIVE'\s
          AND t.status = 'SCHEDULED'
          AND b.status = 'PENDING'
   \s""", nativeQuery = true)
    Page<Object[]> findTripBusRouteInfo(Pageable pageable);


    @Query("""
        SELECT t.driver.id, COUNT(t.id) as total
        FROM Trip t
        WHERE t.status = 'COMPLETED'
          AND MONTH(t.arrivalTime) = :month
          AND YEAR(t.arrivalTime) = :year
          AND (:userId IS NULL OR t.driver.id = :userId)
        GROUP BY t.driver.id
    """)
    List<Object[]> countCompletedTripsByDriver(@Param("month") byte month, @Param("year") short year,@Param("userId") Long userId);


    @Query("""
        SELECT COUNT(t.id) as total
        FROM Trip t
        WHERE t.status = 'COMPLETED'
          AND MONTH(t.arrivalTime) = :month
          AND YEAR(t.arrivalTime) = :year
          AND (:userId IS NULL OR t.driver.id = :userId)
    """)
    Long countCompletedTripsByDriverForOne(@Param("month") byte month, @Param("year") short year,@Param("userId") Long userId);
}
