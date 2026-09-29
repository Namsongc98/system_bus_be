package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 1.1, T6: the trip lookups behind the bus/route delete rules (D2 = A), against real MySQL.
 * Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class TripRepositoryExistsTest {

    private static final List<TripStatus> UNFINISHED = List.of(TripStatus.SCHEDULED, TripStatus.ONGOING);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TripRepository tripRepository;

    private long driverId;
    private long routeWithHistory;
    private long routeWithActiveTrip;
    private long unusedRoute;
    private long busWithHistory;
    private long busWithActiveTrip;
    private long unusedBus;

    @BeforeEach
    void seed() {
        driverId = insert("INSERT INTO users (email, password) VALUES ('d@test.vn', 'x')");
        routeWithHistory = insert("INSERT INTO routes (route_name) VALUES ('HN - HP')");
        routeWithActiveTrip = insert("INSERT INTO routes (route_name) VALUES ('HN - ND')");
        unusedRoute = insert("INSERT INTO routes (route_name) VALUES ('HN - TB')");
        busWithHistory = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-00001', 40)");
        busWithActiveTrip = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-00002', 40)");
        unusedBus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-00003', 40)");

        insertTrip(routeWithHistory, busWithHistory, "COMPLETED");
        insertTrip(routeWithHistory, busWithHistory, "CANCELLED");
        insertTrip(routeWithActiveTrip, busWithActiveTrip, "ONGOING");
    }

    @Test
    void busLookups() {
        assertThat(tripRepository.existsByBusIdAndStatusIn(busWithActiveTrip, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsByBusIdAndStatusIn(busWithHistory, UNFINISHED)).isFalse();
        assertThat(tripRepository.existsByBusId(busWithHistory)).isTrue();
        assertThat(tripRepository.existsByBusId(unusedBus)).isFalse();
    }

    @Test
    void routeLookups() {
        assertThat(tripRepository.existsByRouteIdAndStatusIn(routeWithActiveTrip, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsByRouteIdAndStatusIn(routeWithHistory, UNFINISHED)).isFalse();
        assertThat(tripRepository.existsByRouteId(routeWithHistory)).isTrue();
        assertThat(tripRepository.existsByRouteId(unusedRoute)).isFalse();
    }

    private void insertTrip(long routeId, long busId, String status) {
        jdbc.update("INSERT INTO trips (route_id, bus_id, driver_id, departure_time, status) VALUES (?, ?, ?, NOW(), ?)",
                routeId, busId, driverId, status);
    }

    private long insert(String sql) {
        jdbc.update(sql);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
