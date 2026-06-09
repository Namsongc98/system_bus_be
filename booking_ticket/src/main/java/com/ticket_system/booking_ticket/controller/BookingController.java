package com.ticket_system.booking_ticket.controller;

import com.ticket_system.booking_ticket.service.BookingProducer;
import com.ticket_system.common.Dto.request.TicketRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/booking")
public class BookingController {

  @Autowired
  private BookingProducer bookingProducer;

  @PostMapping
  public void addTicket(@RequestBody TicketRequestDto responseDto){
    bookingProducer.sendBookingEvent(responseDto);
  };

}
