package com.ticket_system.manage_revenue_ticket.Dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BaseSalaryRequestDTO {
    private Long userId;
    private BigDecimal baseSalary;
    private LocalDate effectiveFrom;
}
