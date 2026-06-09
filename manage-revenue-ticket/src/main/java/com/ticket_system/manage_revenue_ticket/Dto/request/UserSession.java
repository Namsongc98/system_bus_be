package com.ticket_system.manage_revenue_ticket.Dto.request;

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
