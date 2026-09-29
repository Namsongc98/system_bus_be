package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.RouteRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.RouteResponse;
import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import com.ticket_system.manage_revenue_ticket.service.RouteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@RoleRequired(UserRole.ADMIN)
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<PageResponse<RouteResponse>>> getRoutes(
            @RequestParam(required = false) RouteStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<RouteResponse> routes = routeService.getRoutes(status, page, size);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", routes));
    }

    @GetMapping("/{routeId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<RouteResponse>> getRoute(@PathVariable Long routeId) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", routeService.getRoute(routeId)));
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<RouteResponse>> createRoute(@Valid @RequestBody RouteRequestDto requestDto) {
        log.debug("Creating route: {}", requestDto.getRouteName());
        RouteResponse created = routeService.createRoute(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Create Successfully", created));
    }

    @PutMapping("/{routeId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<RouteResponse>> updateRoute(
            @PathVariable Long routeId, @Valid @RequestBody RouteRequestDto requestDto) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Update Successfully",
                routeService.updateRoute(routeId, requestDto)));
    }

    @DeleteMapping("/{routeId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Void>> deleteRoute(@PathVariable Long routeId) {
        routeService.deleteRoute(routeId);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Delete successfully", null));
    }
}
