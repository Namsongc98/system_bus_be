package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.RouteRequestDto;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    @Autowired
    RouteService routeService;

    @PostMapping
    ResponseEntity<BaseResponseDto<Route>> createRoute(@RequestBody RouteRequestDto requestDto){
        System.out.println(requestDto);
        Route response =  routeService.createRoute(requestDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Create Successfully", response));
    }

    @PutMapping("/{routeId}")
    ResponseEntity<BaseResponseDto<Route>> updateRoute(@PathVariable String routeId,@RequestBody RouteRequestDto requestDto){
        routeService.updateRoute(Long.parseLong(routeId) , requestDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Update Successfully", null));
    }
}
