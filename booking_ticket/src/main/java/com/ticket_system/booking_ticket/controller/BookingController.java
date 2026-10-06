package com.ticket_system.booking_ticket.controller;

import com.ticket_system.booking_ticket.repository.UserRepository.BookingAccount;
import com.ticket_system.booking_ticket.security.BookingAccountGuard;
import com.ticket_system.booking_ticket.service.BookingProducer;
import com.ticket_system.common.Dto.request.TicketRequestDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/booking")
public class BookingController {

  private final BookingProducer bookingProducer;
  private final BookingAccountGuard accountGuard;

  public BookingController(BookingProducer bookingProducer, BookingAccountGuard accountGuard) {
    this.bookingProducer = bookingProducer;
    this.accountGuard = accountGuard;
  }

  // B33: a customer books for themselves only. customerId and the confirmation email come from the
  // JWT and the database, never from the body; there is no seller on a self-booking.
  // The price is still taken from the body until task 2.2 prices the seat on the server.
  @PostMapping
  public void addTicket(@AuthenticationPrincipal Long userId, @RequestBody TicketRequestDto responseDto){
    BookingAccount account = accountGuard.requireActiveCustomer(userId);
    responseDto.setCustomerId(userId);
    responseDto.setCustomerEmail(account.getEmail());
    responseDto.setSellerId(null);
    bookingProducer.sendBookingEvent(responseDto);
  };

}
