package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class BusService {

    @Autowired
    private BusesRepository busRepository;

    // tạo xe bus
    public Buses save(BusRequest request) {
        Buses bus;
            // Thêm mới xe
            if (busRepository.existsByPlateNumber(request.getPlateNumber())) {
                throw new RuntimeException("Biển số xe đã tồn tại: " + request.getPlateNumber());
            }

            bus = Buses.builder()
                    .plateNumber(request.getPlateNumber())
                    .capacity(request.getCapacity())
                    .status(BusStatus.valueOf(request.getStatus()))
                    .build();
        return busRepository.save(bus);
    }
    // update thông tin xe bus
    public Buses update(Long busId,BusRequest request) {
        Buses bus;
            bus = busRepository.findById(busId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy xe với id: " + busId));
            bus.setPlateNumber(request.getPlateNumber());
            bus.setCapacity(request.getCapacity());
            bus.setStatus(BusStatus.valueOf(request.getStatus().toUpperCase()));
        return busRepository.save(bus);
    }

    public Page<Buses> getBuses(Pageable pageable){
            return busRepository.findAll(pageable);
    }
}
