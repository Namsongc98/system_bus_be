package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.RouteRequestDto;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.service.RouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    ResponseEntity<BaseResponseDto<Route>> createRoute(@RequestBody RouteRequestDto requestDto){
        log.debug("Creating route: {}", requestDto);
        Route response =  routeService.createRoute(requestDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Create Successfully", response));
    }

    @PutMapping("/{routeId}")
    @RoleRequired(UserRole.ADMIN)
    ResponseEntity<BaseResponseDto<Route>> updateRoute(@PathVariable String routeId,@RequestBody RouteRequestDto requestDto){
        routeService.updateRoute(Long.parseLong(routeId) , requestDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Update Successfully", null));
    }
}
