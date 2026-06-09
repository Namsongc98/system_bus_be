package com.ticket_system.booking_ticket.dto.request;

import com.ticket_system.booking_ticket.Enum.TransactionType;
import lombok.Data;

@Data
public class LoyaltyPointsRequest {
    private Long customerId;
    private int points;
    private TransactionType transactionType;
    private String description;
}
