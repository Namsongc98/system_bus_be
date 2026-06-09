package com.ticket_system.manage_revenue_ticket.Dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketBookedEvent {
  private String bookingId;
  private String customerEmail;
  private Long price;
  private String status; // PENDING, SUCCESS
}
