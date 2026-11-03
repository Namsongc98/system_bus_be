package com.ticket_system.manage_revenue_ticket.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 0.3, decision 1 = A: a DB built by the old ddl-auto=update (tables + rows, no flyway_schema_history)
 * is baselined at V1 by the Flyway settings in application.properties, then V2+ run on top and no row is lost.
 * The container starts with V1 + legacy rows but no history; no Flyway property is overridden here.
 * Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class LegacySchemaBaselineTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withInitScripts("db/migration/V1__baseline.sql", "migration/legacy-seed.sql");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void existingSchemaIsBaselinedAtV1AndOnlyLaterMigrationsRun() {
        assertThat(successfulHistory()).containsExactly("1:BASELINE", "2:SQL", "3:SQL", "4:SQL");
    }

    @Test
    void legacyRowsAreKeptAndMigrated() {
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("routes")).isEqualTo(1);
        assertThat(count("buses")).isEqualTo(3);
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("tickets")).isEqualTo(2);

        // V3 maps every old bus status
        assertThat(jdbc.queryForMap("""
                SELECT MAX(CASE WHEN plate_number = '29A-PENDING' THEN status END) AS pending,
                       MAX(CASE WHEN plate_number = '29A-ACTIVE' THEN status END) AS active,
                       MAX(CASE WHEN plate_number = '29A-INACTIVE' THEN status END) AS inactive
                FROM buses
                """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "pending", "AVAILABLE", "active", "IN_USE", "inactive", "MAINTENANCE"));

        // V2: only the non-cancelled ticket holds the seat
        assertThat(jdbc.queryForObject("SELECT active_seat_number FROM tickets WHERE id = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active_seat_number FROM tickets WHERE id = 2", Integer.class))
                .isNull();
    }

    @Test
    void secondMigrateOnBaselinedDbIsNoOp() {
        // Every restart after the first one takes this path.
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(successfulHistory()).containsExactly("1:BASELINE", "2:SQL", "3:SQL", "4:SQL");
    }

    @Test
    void cleanIsDisabled() {
        assertThatThrownBy(flyway::clean)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("cleanDisabled");
        assertThat(count("buses")).isEqualTo(3);
    }

    private List<String> successfulHistory() {
        return jdbc.queryForList(
                "SELECT CONCAT(version, ':', type) FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
