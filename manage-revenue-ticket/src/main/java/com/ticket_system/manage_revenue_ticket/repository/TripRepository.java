package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface TripRepository extends JpaRepository<Trip, Long> {
    boolean existsByBusId(Long busId);

    boolean existsByBusIdAndStatusIn(Long busId, Collection<TripStatus> statuses);

    // Plain read (no lock): BusService locks these by id (lockByIdIn) and reads them again after the bus lock (B37 a).
    @Query("select t.id from Trip t where t.bus.id = :busId and t.status in :statuses")
    List<Long> findIdsByBusIdAndStatusIn(@Param("busId") Long busId,
                                        @Param("statuses") Collection<TripStatus> statuses);

    boolean existsByRouteId(Long routeId);

    boolean existsByRouteIdAndStatusIn(Long routeId, Collection<TripStatus> statuses);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<TripStatus> statuses);

    /**
     * Admin trip list (spec review 1.3 S1). Every filter is optional (null = not applied);
     * {@code from}/{@code to} bound departureTime as [from, to). Route, bus and driver are
     * many-to-one, so fetching them keeps paging in the database.
     */
    @Query(value = """
            select t from Trip t
            join fetch t.route join fetch t.bus join fetch t.driver
            where (:status is null or t.status = :status)
              and (:routeId is null or t.route.id = :routeId)
              and (:busId is null or t.bus.id = :busId)
              and (:driverId is null or t.driver.id = :driverId)
              and (:from is null or t.departureTime >= :from)
              and (:to is null or t.departureTime < :to)
            order by t.departureTime asc, t.id asc
            """,
            countQuery = """
            select count(t) from Trip t
            where (:status is null or t.status = :status)
              and (:routeId is null or t.route.id = :routeId)
              and (:busId is null or t.bus.id = :busId)
              and (:driverId is null or t.driver.id = :driverId)
              and (:from is null or t.departureTime >= :from)
              and (:to is null or t.departureTime < :to)
            """)
    Page<Trip> search(@Param("status") TripStatus status,
                      @Param("routeId") Long routeId,
                      @Param("busId") Long busId,
                      @Param("driverId") Long driverId,
                      @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to,
                      Pageable pageable);

    @Query("select t from Trip t join fetch t.route join fetch t.bus join fetch t.driver where t.id = :id")
    Optional<Trip> findDetailedById(@Param("id") Long id);

    /**
     * Row lock on the trip only (SELECT ... FOR UPDATE on trips). Ticket sales and trip status /
     * edit changes both take it, so a ticket cannot be sold on a trip being cancelled or started.
     * Lock order everywhere: trip(s) by primary key, then route, then buses (id asc), then drivers (id asc).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    /**
     * Row-locks trips by primary key only (B36 b, lead review 1.3 L34). BusService reads the ids of a bus's
     * unfinished trips with a plain query, then locks them here: a locking scan of the (bus_id, ...) index
     * would also hold index entries that TripService.update rewrites at commit while it holds the trip row,
     * and the two would deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id in :ids order by t.id")
    List<Trip> lockByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Another unfinished trip of this bus overlaps [departure, arrival). Touching ends do not
     * overlap; a trip without arrival time occupies only its departure instant.
     */
    @Query("""
            select count(t) > 0 from Trip t
            where t.bus.id = :busId
              and t.status in :statuses
              and (:excludeTripId is null or t.id <> :excludeTripId)
              and t.departureTime < :arrival
              and coalesce(t.arrivalTime, t.departureTime) > :departure
            """)
    boolean existsBusOverlap(@Param("busId") Long busId,
                             @Param("departure") LocalDateTime departure,
                             @Param("arrival") LocalDateTime arrival,
                             @Param("excludeTripId") Long excludeTripId,
                             @Param("statuses") Collection<TripStatus> statuses);

    /** Same rule as {@link #existsBusOverlap} for the driver. */
    @Query("""
            select count(t) > 0 from Trip t
            where t.driver.id = :driverId
              and t.status in :statuses
              and (:excludeTripId is null or t.id <> :excludeTripId)
              and t.departureTime < :arrival
              and coalesce(t.arrivalTime, t.departureTime) > :departure
            """)
    boolean existsDriverOverlap(@Param("driverId") Long driverId,
                                @Param("departure") LocalDateTime departure,
                                @Param("arrival") LocalDateTime arrival,
                                @Param("excludeTripId") Long excludeTripId,
                                @Param("statuses") Collection<TripStatus> statuses);


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

    @Query("""
        SELECT COUNT(t.id)
        FROM Trip t
        WHERE t.status = com.ticket_system.manage_revenue_ticket.Enum.TripStatus.COMPLETED
          AND t.arrivalTime >= :start
          AND t.arrivalTime < :end
    """)
    long countCompletedTrips(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
