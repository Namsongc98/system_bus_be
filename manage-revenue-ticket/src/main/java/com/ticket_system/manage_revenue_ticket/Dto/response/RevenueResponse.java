package com.ticket_system.manage_revenue_ticket.Dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueResponse {

  private Integer tripId;

  private BigDecimal totalRevenue;

  private BigDecimal averageRevenue;

}
