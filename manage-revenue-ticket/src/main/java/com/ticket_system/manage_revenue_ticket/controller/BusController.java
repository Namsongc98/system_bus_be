package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.service.BusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/bus")
public class BusController {

    @Autowired
    private BusService busService;

    @GetMapping
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
    public ResponseEntity<BaseResponseDto<Buses>> postBus(@RequestBody BusRequest request){
        busService.save(request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Post successfully",null));
    }

    @PutMapping("/{busId}")
    public ResponseEntity<BaseResponseDto<Buses>> putBus(@PathVariable String busId,@RequestBody BusRequest request){
        Long busIdParse = Long.parseLong(busId);
        busService.update(busIdParse,request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Put successfully",null));
    }

}
