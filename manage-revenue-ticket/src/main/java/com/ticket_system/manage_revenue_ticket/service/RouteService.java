package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.PageRequests;
import com.ticket_system.manage_revenue_ticket.Dto.request.RouteRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.RouteResponse;
import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.repository.RouteRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {

    private static final String ROUTE_HAS_HISTORY = "Tuyến đã có lịch sử chuyến, hãy chuyển sang INACTIVE";

    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;

    public RouteService(RouteRepository routeRepository, TripRepository tripRepository) {
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RouteResponse> getRoutes(RouteStatus status, int page, int size) {
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Route> routes = status == null
                ? routeRepository.findAll(pageable)
                : routeRepository.findByStatus(status, pageable);
        return PageResponse.from(routes, RouteResponse::from);
    }

    @Transactional(readOnly = true)
    public RouteResponse getRoute(Long routeId) {
        return RouteResponse.from(findRoute(routeId));
    }

    // tạo tuyến đường
    @Transactional
    public RouteResponse createRoute(RouteRequestDto requestDto) {
        Route route = new Route();
        apply(route, requestDto);
        return RouteResponse.from(routeRepository.save(route));
    }

    // thay đổi tuyến đường
    // Row lock first: serialised with trip creation on this route (TripService), see D8 below.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RouteResponse updateRoute(Long routeId, RouteRequestDto requestDto) {
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến với id: " + routeId));
        // D8 = A (spec 1.3): a route with a scheduled or ongoing trip cannot be deactivated.
        if (requestDto.getStatus() == RouteStatus.INACTIVE && route.getStatus() != RouteStatus.INACTIVE
                && tripRepository.existsByRouteIdAndStatusIn(routeId, BusService.UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException("Tuyến " + route.getRouteName() + " còn chuyến chưa kết thúc, không thể ngừng hoạt động");
        }
        apply(route, requestDto);
        return RouteResponse.from(routeRepository.save(route));
    }

    // D2 = A: hard delete only when no trip has ever used the route.
    @Transactional
    public void deleteRoute(Long routeId) {
        Route route = findRoute(routeId);
        if (tripRepository.existsByRouteIdAndStatusIn(routeId, BusService.UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException("Tuyến đang có chuyến chưa kết thúc");
        }
        if (tripRepository.existsByRouteId(routeId)) {
            throw new ConflictException(ROUTE_HAS_HISTORY);
        }
        try {
            routeRepository.delete(route);
            routeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // A trip was created on this route between the check above and the delete (FK trips_fk_route).
            throw new ConflictException(ROUTE_HAS_HISTORY);
        }
    }

    private Route findRoute(Long routeId) {
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến với id: " + routeId));
    }

    private static void apply(Route route, RouteRequestDto requestDto) {
        String startPoint = requestDto.getStartPoint().trim();
        String endPoint = requestDto.getEndPoint().trim();
        if (startPoint.equalsIgnoreCase(endPoint)) {
            throw new IllegalArgumentException("Điểm đi và điểm đến phải khác nhau");
        }
        route.setRouteName(requestDto.getRouteName().trim());
        route.setStartPoint(startPoint);
        route.setEndPoint(endPoint);
        route.setDistanceKm(requestDto.getDistanceKm());
        route.setStatus(requestDto.getStatus());
    }
}
