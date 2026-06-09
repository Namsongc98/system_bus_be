package com.ticket_system.manage_revenue_ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableCaching
@SpringBootApplication(scanBasePackages = "com.ticket_system")
public class ManageRevenueTicketApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManageRevenueTicketApplication.class, args);
	}

}
