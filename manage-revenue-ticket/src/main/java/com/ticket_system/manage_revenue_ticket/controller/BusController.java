package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.BusResponse;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.service.BusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/bus")
@RoleRequired(UserRole.ADMIN)
public class BusController {

    private final BusService busService;

    public BusController(BusService busService) {
        this.busService = busService;
    }

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<PageResponse<BusResponse>>> getBuses(
            @RequestParam(required = false) BusStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        PageResponse<BusResponse> buses = busService.getBuses(status, page, size);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", buses));
    }

    @GetMapping("/{busId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BusResponse>> getBus(@PathVariable Long busId) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", busService.getBus(busId)));
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BusResponse>> postBus(@Valid @RequestBody BusRequest request) {
        BusResponse created = busService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Post successfully", created));
    }

    @PutMapping("/{busId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BusResponse>> putBus(
            @PathVariable Long busId, @Valid @RequestBody BusRequest request) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Put successfully", busService.update(busId, request)));
    }

    @DeleteMapping("/{busId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Void>> deleteBus(@PathVariable Long busId) {
        busService.delete(busId);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Delete successfully", null));
    }
}
