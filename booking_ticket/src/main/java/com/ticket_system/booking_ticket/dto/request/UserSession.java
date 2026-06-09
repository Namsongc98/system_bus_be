package com.ticket_system.booking_ticket.dto.request;

import lombok.*;

import java.time.LocalDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {
  private Long userId;
  private String email;
  private String role;
  private LocalDateTime loginTime;
}
