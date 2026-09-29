package com.ticket_system.manage_revenue_ticket.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V3 maps existing rows PENDING→AVAILABLE, ACTIVE→IN_USE, INACTIVE→MAINTENANCE (task 1.1, D1 = A, T5).
 * Migrates a fresh schema to V2, seeds old values, then applies V3. Requires a running Docker daemon.
 */
@Testcontainers
class BusStatusMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void v3MapsOldStatusValuesAndDropsThem() {
        DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        flyway(dataSource, "2").migrate();
        jdbc.update("INSERT INTO buses (plate_number, capacity, status) VALUES "
                + "('29A-00001', 40, 'PENDING'), ('29A-00002', 40, 'ACTIVE'), ('29A-00003', 40, 'INACTIVE')");

        flyway(dataSource, "3").migrate();

        Map<String, String> statusByPlate = new java.util.HashMap<>();
        jdbc.query("SELECT plate_number, status FROM buses",
                rs -> { statusByPlate.put(rs.getString(1), rs.getString(2)); });
        assertThat(statusByPlate).containsExactlyInAnyOrderEntriesOf(Map.of(
                "29A-00001", "AVAILABLE",
                "29A-00002", "IN_USE",
                "29A-00003", "MAINTENANCE"));

        jdbc.update("INSERT INTO buses (plate_number, capacity) VALUES ('29A-00004', 40)");
        assertThat(jdbc.queryForObject("SELECT status FROM buses WHERE plate_number = '29A-00004'", String.class))
                .isEqualTo("AVAILABLE");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO buses (plate_number, capacity, status) VALUES ('29A-00005', 40, 'PENDING')"))
                .hasMessageContaining("status");
    }

    private static Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
