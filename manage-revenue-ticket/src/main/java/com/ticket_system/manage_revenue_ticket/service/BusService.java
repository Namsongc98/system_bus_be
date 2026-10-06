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
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import com.ticket_system.manage_revenue_ticket.repository.TicketRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusService {

    static final List<TripStatus> UNFINISHED_TRIP_STATUSES = List.of(TripStatus.SCHEDULED, TripStatus.ONGOING);
    private static final String BUS_HAS_HISTORY = "Xe đã có lịch sử chuyến, hãy chuyển sang MAINTENANCE";
    // Name of the unique constraint on buses.plate_number (V1__baseline.sql).
    private static final String PLATE_UNIQUE_CONSTRAINT = "plate_number";

    private final BusesRepository busRepository;
    private final TripRepository tripRepository;
    private final TicketRepository ticketRepository;

    public BusService(BusesRepository busRepository, TripRepository tripRepository,
                      TicketRepository ticketRepository) {
        this.busRepository = busRepository;
        this.tripRepository = tripRepository;
        this.ticketRepository = ticketRepository;
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
    // Row lock first: serialised with trip creation on this bus (TripService), so the D8 check below
    // sees a trip another admin is creating right now.
    // B36 b: the unfinished trips of the bus are locked first (trip before bus, the order TripService and
    // TicketService use), so no ticket is sold on them while a smaller capacity is checked and saved.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BusResponse update(Long busId, BusRequest request) {
        // Ids first (plain read), then row locks by primary key: never a locking scan of the bus index (L34).
        List<Long> tripIds = tripRepository.findIdsByBusIdAndStatusIn(busId, UNFINISHED_TRIP_STATUSES);
        Set<Long> lockedTrips = tripIds.isEmpty() ? Set.of() : tripRepository.lockByIdIn(tripIds).stream()
                .map(Trip::getId)
                .collect(Collectors.toSet());
        Buses bus = busRepository.findByIdForUpdate(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe với id: " + busId));
        if (request.getCapacity() != null && request.getCapacity() < bus.getCapacity()) {
            // B37 a: a trip created on this bus between the trip locks and the bus lock is not locked here,
            // so a seat could be sold on it with the old capacity. Locking it now would invert the
            // trip → bus order; the shrink is refused instead and the admin retries.
            if (!lockedTrips.containsAll(tripRepository.findIdsByBusIdAndStatusIn(busId, UNFINISHED_TRIP_STATUSES))) {
                throw new ConflictException("Xe " + bus.getPlateNumber() + " vừa được gán chuyến mới, vui lòng thử lại");
            }
            int required = ticketRepository.findRequiredCapacityForBus(busId);
            if (request.getCapacity() < required) {
                throw new ConflictException("Xe " + bus.getPlateNumber() + " đã bán tới ghế " + required
                        + " trên chuyến chưa kết thúc, không thể giảm sức chứa xuống " + request.getCapacity());
            }
        }
        boolean staysInUse = bus.getStatus() == BusStatus.IN_USE && request.getStatus() == BusStatus.IN_USE;
        if (!staysInUse) {
            rejectManualInUse(request.getStatus());
        }
        // IN_USE is recomputed by TripService (spec 1.3 D1); a stale IN_USE bus with no unfinished
        // trip may still be moved to AVAILABLE/MAINTENANCE by hand.
        if (bus.getStatus() == BusStatus.IN_USE && !staysInUse
                && tripRepository.existsByBusIdAndStatusIn(busId, UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException("Xe " + bus.getPlateNumber() + " đang chạy chuyến, không thể đổi trạng thái");
        }
        // D8 = A (spec 1.3): no maintenance while the bus still has a scheduled or ongoing trip.
        if (request.getStatus() == BusStatus.MAINTENANCE && bus.getStatus() != BusStatus.MAINTENANCE
                && tripRepository.existsByBusIdAndStatusIn(busId, UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException("Xe " + bus.getPlateNumber() + " còn chuyến chưa kết thúc, không thể chuyển sang bảo dưỡng");
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
