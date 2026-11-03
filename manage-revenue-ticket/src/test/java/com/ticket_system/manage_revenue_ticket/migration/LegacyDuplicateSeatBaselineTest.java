package com.ticket_system.manage_revenue_ticket.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 0.3 (L3): an old DB with two non-cancelled tickets on one seat is baselined, then V2 fails. The startup must
 * stop with no row lost and no successful V2 in the history; after the duplicate is removed by hand,
 * {@code repair} + {@code migrate} finish V2-V4. No Spring context: it would not start while V2 fails.
 * Flyway uses the same baseline settings as application.properties. Requires a running Docker daemon.
 */
@Testcontainers
class LegacyDuplicateSeatBaselineTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withInitScripts("db/migration/V1__baseline.sql", "migration/legacy-duplicate-seat-seed.sql");

    @Test
    void duplicateActiveSeatStopsV2WithoutLosingRowsAndRecoversAfterCleanup() throws IOException {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway flyway = flywayAsConfigured(dataSource);

        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM buses WHERE id = 1", String.class)).isEqualTo("ACTIVE");
        // V2 is a single ALTER: MySQL undoes it as a whole, the table keeps its old shape
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'active_seat_number'
                """, Integer.class)).isZero();
        assertThat(successfulHistory(jdbc)).containsExactly("1:BASELINE");

        // Recovery: free the seat by hand, repair the failed entry, migrate again
        jdbc.update("UPDATE tickets SET status_ticket = 'CANCELLED' WHERE id = 2");
        flyway.repair();
        flyway.migrate();

        assertThat(successfulHistory(jdbc)).containsExactly("1:BASELINE", "2:SQL", "3:SQL", "4:SQL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM buses WHERE id = 1", String.class)).isEqualTo("IN_USE");
    }

    private static Flyway flywayAsConfigured(DriverManagerDataSource dataSource) throws IOException {
        Properties app = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(app.getProperty("spring.flyway.locations"))
                .baselineOnMigrate(Boolean.parseBoolean(app.getProperty("spring.flyway.baseline-on-migrate")))
                .baselineVersion(app.getProperty("spring.flyway.baseline-version"))
                .cleanDisabled(Boolean.parseBoolean(app.getProperty("spring.flyway.clean-disabled")))
                .load();
    }

    private static List<String> successfulHistory(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT CONCAT(version, ':', type) FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
    }
}
