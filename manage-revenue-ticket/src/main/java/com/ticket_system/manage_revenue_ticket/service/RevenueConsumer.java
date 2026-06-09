package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class RevenueConsumer {
  @KafkaListener(topics = "order-events", groupId = "booking-group")
  public void consumeBooking(TicketRequestDto booking) {
    if(booking.getStatus().equals("ERROR")){
      throw new RuntimeException("Test consumer crash");
    }

    System.out.println("Process: "+booking);
  }
}
