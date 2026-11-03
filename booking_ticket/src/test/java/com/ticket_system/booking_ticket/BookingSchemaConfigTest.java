package com.ticket_system.booking_ticket;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 0.3, decision 2: booking_ticket shares the DB owned by manage-revenue-ticket's Flyway, so Hibernate here only
 * validates and the service never runs migrations. The repository tests pin ddl-auto themselves, so this reads
 * the real config files instead. No Docker needed.
 */
class BookingSchemaConfigTest {

    @Test
    void defaultProfileOnlyValidatesTheSchema() throws IOException {
        Properties app = load("application.properties");

        assertThat(app.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("${JPA_DDL_AUTO:validate}");
        assertThat(app).doesNotContainKey("spring.flyway.enabled");
    }

    @Test
    void prodProfileOnlyValidatesTheSchema() throws IOException {
        Properties prod = load("application-prod.properties");

        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(prod).doesNotContainKey("spring.flyway.enabled");
    }

    private static Properties load(String file) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
    }
}
