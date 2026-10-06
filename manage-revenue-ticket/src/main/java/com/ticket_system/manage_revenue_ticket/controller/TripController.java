package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.TripRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.TripStatusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.TripResponse;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.service.TripService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** Admin trip management (task 1.3). */
@RestController
@RequestMapping("/api/trip")
@RoleRequired(UserRole.ADMIN)
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<PageResponse<TripResponse>>> getTrips(
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) Long busId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully",
                tripService.getTrips(status, routeId, busId, driverId, from, to, page, size)));
    }

    @GetMapping("/{tripId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<TripResponse>> getTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", tripService.getTrip(tripId)));
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<TripResponse>> createTrip(@Valid @RequestBody TripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Post successfully", tripService.create(request)));
    }

    @PutMapping("/{tripId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<TripResponse>> updateTrip(
            @PathVariable Long tripId, @Valid @RequestBody TripRequest request) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Put successfully", tripService.update(tripId, request)));
    }

    @PatchMapping("/{tripId}/status")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<TripResponse>> changeStatus(
            @PathVariable Long tripId, @Valid @RequestBody TripStatusRequest request) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Patch successfully",
                tripService.changeStatus(tripId, request.getStatus())));
    }

    @DeleteMapping("/{tripId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Void>> deleteTrip(@PathVariable Long tripId) {
        tripService.delete(tripId);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Delete successfully", null));
    }
}
