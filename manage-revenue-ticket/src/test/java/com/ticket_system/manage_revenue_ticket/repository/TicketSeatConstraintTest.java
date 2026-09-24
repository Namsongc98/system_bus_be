package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.entity.Ticket;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 0.8: V2 unique (trip, active seat) and the per-trip seat queries, against real MySQL.
 * Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class TicketSeatConstraintTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TripRepository tripRepository;

    private long customerId;
    private long busId;
    private long tripOne;
    private long tripTwo;

    @BeforeEach
    void seed() {
        customerId = insert("INSERT INTO users (email, password) VALUES ('c@test.vn', 'x')");
        long driverId = insert("INSERT INTO users (email, password) VALUES ('d@test.vn', 'x')");
        long routeId = insert("INSERT INTO routes (route_name) VALUES ('HN - HP')");
        busId = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-00001', 2)");
        tripOne = insertTrip(routeId, driverId);
        tripTwo = insertTrip(routeId, driverId);
    }

    @Test
    void duplicateActiveSeatOnSameTripIsRejectedByUniqueConstraint() {
        Trip trip = tripRepository.findById(tripOne).orElseThrow();
        ticketRepository.saveAndFlush(ticket(trip, 5, TicketStatus.PENDING));

        assertThatThrownBy(() -> ticketRepository.saveAndFlush(ticket(trip, 5, TicketStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("uk_tickets_trip_active_seat"));
    }

    @Test
    void sameSeatOnAnotherTripIsAllowed() {
        insertTicket(tripOne, 5, "PENDING");

        assertThatCode(() -> insertTicket(tripTwo, 5, "PENDING")).doesNotThrowAnyException();
    }

    @Test
    void cancelledTicketFreesItsSeat() {
        insertTicket(tripOne, 5, "CANCELLED");

        assertThatCode(() -> insertTicket(tripOne, 5, "PENDING")).doesNotThrowAnyException();
        assertThatCode(() -> insertTicket(tripOne, 6, "CANCELLED")).doesNotThrowAnyException();
        assertThatCode(() -> insertTicket(tripOne, 6, "CANCELLED")).doesNotThrowAnyException();
    }

    @Test
    void activeCountIsPerTripAndIgnoresCancelled() {
        // Trip 1 of the 2-seat bus is full; trip 2 of the same bus must still be bookable.
        insertTicket(tripOne, 1, "PENDING");
        insertTicket(tripOne, 2, "SUCCESS");
        insertTicket(tripOne, 3, "CANCELLED");

        assertThat(ticketRepository.countActiveTicketsByTripId(tripOne)).isEqualTo(2);
        assertThat(ticketRepository.countActiveTicketsByTripId(tripTwo)).isZero();
    }

    @Test
    void existsActiveSeatSkipsCancelledAndExcludedTicket() {
        long active = insertTicket(tripOne, 1, "PENDING");
        insertTicket(tripOne, 2, "CANCELLED");

        assertThat(ticketRepository.existsActiveSeat(tripOne, 1, null)).isTrue();
        assertThat(ticketRepository.existsActiveSeat(tripOne, 1, active)).isFalse();
        assertThat(ticketRepository.existsActiveSeat(tripOne, 2, null)).isFalse();
        assertThat(ticketRepository.existsActiveSeat(tripTwo, 1, null)).isFalse();
    }

    @Test
    void existsCancelledSeatOnlyMatchesCancelledOnSameTrip() {
        insertTicket(tripOne, 1, "CANCELLED");
        insertTicket(tripOne, 2, "PENDING");

        assertThat(ticketRepository.existsCancelledSeat(tripOne, 1)).isTrue();
        assertThat(ticketRepository.existsCancelledSeat(tripOne, 2)).isFalse();
        assertThat(ticketRepository.existsCancelledSeat(tripTwo, 1)).isFalse();
    }

    private long insertTrip(long routeId, long driverId) {
        return insert("INSERT INTO trips (route_id, bus_id, driver_id, departure_time) VALUES ("
                + routeId + ", " + busId + ", " + driverId + ", NOW())");
    }

    private long insertTicket(long tripId, int seat, String status) {
        jdbc.update("INSERT INTO tickets (trip_id, customer_id, status_ticket, seat_number, price) "
                + "VALUES (?, ?, ?, ?, 100000)", tripId, customerId, status, seat);
        return lastId();
    }

    private long insert(String sql) {
        jdbc.update(sql);
        return lastId();
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // Not builder(): @Builder leaves statusTicket null.
    private static Ticket ticket(Trip trip, int seat, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setTrip(trip);
        ticket.setSeatNumber(seat);
        ticket.setStatusTicket(status);
        ticket.setPrice(new BigDecimal("100000"));
        return ticket;
    }
}
