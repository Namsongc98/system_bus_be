package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.PageRequests;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.BusResponse;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BusService {

    static final List<TripStatus> UNFINISHED_TRIP_STATUSES = List.of(TripStatus.SCHEDULED, TripStatus.ONGOING);
    private static final String BUS_HAS_HISTORY = "Xe đã có lịch sử chuyến, hãy chuyển sang MAINTENANCE";
    // Name of the unique constraint on buses.plate_number (V1__baseline.sql).
    private static final String PLATE_UNIQUE_CONSTRAINT = "plate_number";

    private final BusesRepository busRepository;
    private final TripRepository tripRepository;

    public BusService(BusesRepository busRepository, TripRepository tripRepository) {
        this.busRepository = busRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BusResponse> getBuses(BusStatus status, int page, int size) {
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Buses> buses = status == null
                ? busRepository.findAll(pageable)
                : busRepository.findByStatus(status, pageable);
        return PageResponse.from(buses, BusResponse::from);
    }

    @Transactional(readOnly = true)
    public BusResponse getBus(Long busId) {
        return BusResponse.from(findBus(busId));
    }

    // tạo xe bus
    @Transactional
    public BusResponse create(BusRequest request) {
        rejectManualInUse(request.getStatus());
        String plateNumber = request.getPlateNumber().trim();
        if (busRepository.existsByPlateNumber(plateNumber)) {
            throw duplicatePlate(plateNumber);
        }
        Buses bus = Buses.builder()
                .plateNumber(plateNumber)
                .capacity(request.getCapacity())
                .status(request.getStatus())
                .build();
        return BusResponse.from(saveUniquePlate(bus));
    }

    // update thông tin xe bus
    @Transactional
    public BusResponse update(Long busId, BusRequest request) {
        Buses bus = findBus(busId);
        boolean staysInUse = bus.getStatus() == BusStatus.IN_USE && request.getStatus() == BusStatus.IN_USE;
        if (!staysInUse) {
            rejectManualInUse(request.getStatus());
        }
        // IN_USE is only binding while a trip is unfinished. Nothing resets it when a trip ends yet
        // (task 1.3), so an IN_USE bus with no unfinished trip may be moved to AVAILABLE/MAINTENANCE.
        if (bus.getStatus() == BusStatus.IN_USE && !staysInUse
                && tripRepository.existsByBusIdAndStatusIn(busId, UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException("Xe " + bus.getPlateNumber() + " đang chạy chuyến, không thể đổi trạng thái");
        }
        String plateNumber = request.getPlateNumber().trim();
        if (busRepository.existsByPlateNumberAndIdNot(plateNumber, busId)) {
            throw duplicatePlate(plateNumber);
        }
        bus.setPlateNumber(plateNumber);
        bus.setCapacity(request.getCapacity());
        bus.setStatus(request.getStatus());
        return BusResponse.from(saveUniquePlate(bus));
    }

    // D2 = A: hard delete only when the bus has never been assigned to a trip.
    @Transactional
    public void delete(Long busId) {
        Buses bus = findBus(busId);
        if (tripRepository.existsByBusIdAndStatusIn(busId, UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException("Xe đang được gán cho chuyến chưa kết thúc");
        }
        if (tripRepository.existsByBusId(busId)) {
            throw new ConflictException(BUS_HAS_HISTORY);
        }
        try {
            busRepository.delete(bus);
            busRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // A trip was created for this bus between the check above and the delete (FK trips_fk_bus).
            throw new ConflictException(BUS_HAS_HISTORY);
        }
    }

    private Buses findBus(Long busId) {
        return busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe với id: " + busId));
    }

    // IN_USE is set by the system when a trip is created (TripService), never by an admin.
    private static void rejectManualInUse(BusStatus status) {
        if (status == BusStatus.IN_USE) {
            throw new IllegalArgumentException("Không thể đặt trạng thái IN_USE thủ công");
        }
    }

    // The exists-check above misses two concurrent requests with the same plate; the unique
    // constraint catches the second one, which must still be a 409, not a 500. Any other
    // integrity violation is not a duplicate plate and is rethrown unchanged.
    private Buses saveUniquePlate(Buses bus) {
        try {
            return busRepository.saveAndFlush(bus);
        } catch (DataIntegrityViolationException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains(PLATE_UNIQUE_CONSTRAINT)) {
                throw duplicatePlate(bus.getPlateNumber());
            }
            throw e;
        }
    }

    private static ConflictException duplicatePlate(String plateNumber) {
        return new ConflictException("Biển số xe đã tồn tại: " + plateNumber);
    }
}
