package com.ticket_system.booking_ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.ticket_system")
@EnableFeignClients
public class BookingTicketApplication {
  public static void main(String[] args) {
    SpringApplication.run(BookingTicketApplication.class, args);
  }
}
