package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.Dto.response.AdminDashboardResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.RevenueTrendResponse;
import com.ticket_system.manage_revenue_ticket.exception.InvalidDashboardPeriodException;
import com.ticket_system.manage_revenue_ticket.projection.DashboardLoyalCustomerProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardRecentBookingProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardTopRouteProjection;
import com.ticket_system.manage_revenue_ticket.repository.TicketRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {
    private static final int TOP_ROUTE_LIMIT = 4;
    private static final int LOYAL_CUSTOMER_LIMIT = 3;
    private static final int RECENT_BOOKING_LIMIT = 4;
    private static final int TREND_BUCKET_COUNT = 6;

    private final TicketRepository ticketRepository;
    private final TripRepository tripRepository;
    private final Clock clock;

    @Autowired
    public AdminDashboardService(TicketRepository ticketRepository, TripRepository tripRepository) {
        this(ticketRepository, tripRepository, Clock.system(AdminDashboardTime.ZONE_ID));
    }

    AdminDashboardService(TicketRepository ticketRepository, TripRepository tripRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.tripRepository = tripRepository;
        this.clock = clock;
    }

    public AdminDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDate currentStart = today.withDayOfMonth(1);
        LocalDate currentEndExclusive = currentStart.plusMonths(1);
        LocalDate previousStart = currentStart.minusMonths(1);

        LocalDateTime currentStartTime = currentStart.atStartOfDay();
        LocalDateTime currentEndTime = currentEndExclusive.atStartOfDay();
        LocalDateTime previousStartTime = previousStart.atStartOfDay();

        BigDecimal currentRevenue = zeroIfNull(
                ticketRepository.sumSuccessfulTicketRevenue(currentStartTime, currentEndTime)
        );
        BigDecimal previousRevenue = zeroIfNull(
                ticketRepository.sumSuccessfulTicketRevenue(previousStartTime, currentStartTime)
        );
        long currentTrips = tripRepository.countCompletedTrips(currentStartTime, currentEndTime);
        long previousTrips = tripRepository.countCompletedTrips(previousStartTime, currentStartTime);
        long currentTickets = ticketRepository.countSuccessfulTickets(currentStartTime, currentEndTime);
        long previousTickets = ticketRepository.countSuccessfulTickets(previousStartTime, currentStartTime);
        long currentCustomers = ticketRepository.countActiveCustomers(currentStartTime, currentEndTime);
        long previousCustomers = ticketRepository.countActiveCustomers(previousStartTime, currentStartTime);

        AdminDashboardResponse.Kpis kpis = new AdminDashboardResponse.Kpis(
                currentRevenue,
                calculateTrend(currentRevenue, previousRevenue),
                currentTrips,
                calculateTrend(currentTrips, previousTrips),
                currentTickets,
                calculateTrend(currentTickets, previousTickets),
                currentCustomers,
                calculateTrend(currentCustomers, previousCustomers)
        );

        List<AdminDashboardResponse.TopRoute> topRoutes = mapTopRoutes(
                ticketRepository.findTopRoutes(
                        currentStartTime,
                        currentEndTime,
                        PageRequest.of(0, TOP_ROUTE_LIMIT)
                ),
                currentTickets
        );
        List<AdminDashboardResponse.LoyalCustomer> loyalCustomers = mapLoyalCustomers(
                ticketRepository.findLoyalCustomers(
                        currentStartTime,
                        currentEndTime,
                        PageRequest.of(0, LOYAL_CUSTOMER_LIMIT)
                )
        );
        List<AdminDashboardResponse.RecentBooking> recentBookings = ticketRepository
                .findRecentSuccessfulBookings(PageRequest.of(0, RECENT_BOOKING_LIMIT))
                .stream()
                .map(this::mapRecentBooking)
                .toList();

        return new AdminDashboardResponse(
                new AdminDashboardResponse.Period(currentStart, currentEndExclusive.minusDays(1)),
                kpis,
                topRoutes,
                loyalCustomers,
                recentBookings
        );
    }

    public List<RevenueTrendResponse> getRevenueTrend(String period) {
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "monthly" -> getMonthlyRevenueTrend();
            case "weekly" -> getWeeklyRevenueTrend();
            default -> throw new InvalidDashboardPeriodException();
        };
    }

    private List<RevenueTrendResponse> getMonthlyRevenueTrend() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        List<RevenueTrendResponse> buckets = new ArrayList<>(TREND_BUCKET_COUNT);

        for (int offset = TREND_BUCKET_COUNT - 1; offset >= 0; offset--) {
            YearMonth month = currentMonth.minusMonths(offset);
            LocalDate start = month.atDay(1);
            LocalDate endExclusive = month.plusMonths(1).atDay(1);
            buckets.add(new RevenueTrendResponse(
                    month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH),
                    start,
                    endExclusive.minusDays(1),
                    revenueBetween(start, endExclusive)
            ));
        }
        return buckets;
    }

    private List<RevenueTrendResponse> getWeeklyRevenueTrend() {
        LocalDate currentWeekStart = LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
        List<RevenueTrendResponse> buckets = new ArrayList<>(TREND_BUCKET_COUNT);

        for (int offset = TREND_BUCKET_COUNT - 1; offset >= 0; offset--) {
            LocalDate start = currentWeekStart.minusWeeks(offset);
            LocalDate endExclusive = start.plusWeeks(1);
            buckets.add(new RevenueTrendResponse(
                    start.format(labelFormatter),
                    start,
                    endExclusive.minusDays(1),
                    revenueBetween(start, endExclusive)
            ));
        }
        return buckets;
    }

    private BigDecimal revenueBetween(LocalDate start, LocalDate endExclusive) {
        return zeroIfNull(ticketRepository.sumSuccessfulTicketRevenue(
                start.atStartOfDay(),
                endExclusive.atStartOfDay()
        ));
    }

    private List<AdminDashboardResponse.TopRoute> mapTopRoutes(
            List<DashboardTopRouteProjection> rows,
            long totalTickets
    ) {
        return rows.stream()
                .map(row -> new AdminDashboardResponse.TopRoute(
                        row.getRouteId(),
                        row.getRouteName(),
                        row.getTicketsSold(),
                        totalTickets == 0
                                ? BigDecimal.ZERO
                                : BigDecimal.valueOf(row.getTicketsSold())
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(BigDecimal.valueOf(totalTickets), 1, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private List<AdminDashboardResponse.LoyalCustomer> mapLoyalCustomers(
            List<DashboardLoyalCustomerProjection> rows
    ) {
        List<AdminDashboardResponse.LoyalCustomer> customers = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            DashboardLoyalCustomerProjection row = rows.get(index);
            customers.add(new AdminDashboardResponse.LoyalCustomer(
                    row.getCustomerId(),
                    row.getCustomerName(),
                    row.getTrips(),
                    zeroIfNull(row.getTotalSpent()),
                    index + 1
            ));
        }
        return customers;
    }

    private AdminDashboardResponse.RecentBooking mapRecentBooking(
            DashboardRecentBookingProjection row
    ) {
        return new AdminDashboardResponse.RecentBooking(
                row.getTicketId(),
                row.getCustomerName(),
                row.getRouteName(),
                row.getSeatNumber(),
                zeroIfNull(row.getAmount()),
                row.getOccurredAt()
        );
    }

    private BigDecimal calculateTrend(long current, long previous) {
        return calculateTrend(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private BigDecimal calculateTrend(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
