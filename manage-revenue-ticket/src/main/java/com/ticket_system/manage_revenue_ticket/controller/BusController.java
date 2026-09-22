package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.service.BusService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
    ResponseEntity<BaseResponseDto<Page<Buses>>> getBuses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        Pageable pageable = PageRequest.of(page, size);
        Page<Buses> listBus = busService.getBuses(pageable);
        Map<String, Object> response = new HashMap<>();
        response.put("content", listBus.getContent());
        response.put("currentPage", listBus.getNumber());
        response.put("totalItems", listBus.getTotalElements());
        response.put("totalPages", listBus.getTotalPages());
        return ResponseEntity.ok(BaseResponseDto.success(200,"Get Successfully", listBus));
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Buses>> postBus(@RequestBody BusRequest request){
        busService.save(request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Post successfully",null));
    }

    @PutMapping("/{busId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Buses>> putBus(@PathVariable String busId,@RequestBody BusRequest request){
        Long busIdParse = Long.parseLong(busId);
        busService.update(busIdParse,request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Put successfully",null));
    }

}
