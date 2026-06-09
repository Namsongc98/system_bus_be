package com.ticket_system.common.Dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class TicketRequestDto {

  private Long tripId;
  private Long sellerId;
  private Long customerId;
  private String customerEmail;
  private String status;
  private Integer seatNumber;
  private BigDecimal price;
  private LocalDateTime issuedAt;
}
