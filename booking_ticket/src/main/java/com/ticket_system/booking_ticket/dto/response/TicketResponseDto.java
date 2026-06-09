package com.ticket_system.booking_ticket.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketResponseDto {

    private Long tripId;
    private Long sellerId;
    private Long customerId;
    private String customerEmail;
    private String status;
    private Integer seatNumber;
    private BigDecimal price;
    private LocalDateTime issuedAt;
}
