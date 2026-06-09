package com.ticket_system.booking_ticket.client;
import com.ticket_system.booking_ticket.dto.request.BaseLoyaltyPointsRequest;
import com.ticket_system.booking_ticket.dto.request.LoyaltyPointsRequest;
import com.ticket_system.booking_ticket.dto.request.TripRequestDto;
import com.ticket_system.booking_ticket.dto.request.UserRequestDto;

import com.ticket_system.booking_ticket.entity.Trip;
import com.ticket_system.booking_ticket.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "manage-revenue", url = "http://localhost:8082")
public interface RevenueClient {

  @GetMapping("/users/{id}")
  User getUser(@PathVariable Long id);

  @PutMapping("/users/{id}/status")
  void updateUserStatus(@PathVariable Long id,
                        @RequestParam String status);

  @GetMapping("/loyalty/{customerId}")
  List<LoyaltyPointsRequest> getLoyaltyPoints(@PathVariable Long customerId);

  @PostMapping("/loyalty")
  LoyaltyPointsRequest savePoint(@RequestBody LoyaltyPointsRequest dto);

  @GetMapping("/base-loyalty/{role}")
  BaseLoyaltyPointsRequest getBasePoint(@PathVariable String role);

  @GetMapping("/trips/{id}")
  Trip getTrip(@PathVariable Long id);

}
