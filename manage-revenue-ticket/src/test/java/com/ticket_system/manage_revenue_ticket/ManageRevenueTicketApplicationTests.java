package com.ticket_system.manage_revenue_ticket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a throwaway MySQL container, so it
 * runs anywhere Docker runs without local DB credentials or other secrets.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "spring.kafka.listener.auto-startup=false"
})
class ManageRevenueTicketApplicationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void contextLoads() {
    }
}
