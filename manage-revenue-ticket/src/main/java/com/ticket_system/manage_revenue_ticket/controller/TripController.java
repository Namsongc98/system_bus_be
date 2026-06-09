package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.TripRequestDto;

import com.ticket_system.manage_revenue_ticket.Dto.response.RevenueResponse;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.service.TripService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trip")
public class TripController {
    @Autowired
    private TripService tripService;

    @PostMapping
    ResponseEntity<BaseResponseDto<Trip>> createTrip(@RequestBody TripRequestDto requestDto){
        Trip trip = tripService.createTrip(requestDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"create success", trip));
    }
    // các chuyến đi đã lên lịch
    @GetMapping("/scheduled")
    ResponseEntity<BaseResponseDto<Page<Map<String, Object>>>> getTripScheduled(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>>  trip = tripService.getTripScheduled(pageable);
        return ResponseEntity.ok(BaseResponseDto.success(200,"create success", trip));
    }


    @PutMapping("/{tripId}")
    ResponseEntity<BaseResponseDto<Trip>> updateTrip(@PathVariable String tripId,@RequestBody TripRequestDto requestDto ){
        Trip trip = tripService.updateTrip(Long.parseLong(tripId),requestDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"create success", trip));
    }



  public TripController(TripService revenueTripService) {
    this.tripService = revenueTripService;
  }

  @GetMapping("/{tripId}/revenue")
  public RevenueResponse getRevenue(@PathVariable int tripId) {
    return tripService.getRevenue(tripId);
  }

}
