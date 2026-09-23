package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.Dto.response.AdminDashboardResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.RevenueTrendResponse;
import com.ticket_system.manage_revenue_ticket.projection.DashboardLoyalCustomerProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardRecentBookingProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardTopRouteProjection;
import com.ticket_system.manage_revenue_ticket.repository.TicketRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-11T03:00:00Z"),
            ZONE_ID
    );

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TripRepository tripRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(ticketRepository, tripRepository, CLOCK);
    }

    @Test
    void getDashboardBuildsCurrentMonthSnapshotAndTrends() {
        when(ticketRepository.sumSuccessfulTicketRevenue(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0)
        )).thenReturn(new BigDecimal("125500000"));
        when(ticketRepository.sumSuccessfulTicketRevenue(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        )).thenReturn(new BigDecimal("100000000"));
        when(tripRepository.countCompletedTrips(any(), any())).thenReturn(108L, 100L);
        when(ticketRepository.countSuccessfulTickets(any(), any())).thenReturn(120L, 100L);
        when(ticketRepository.countActiveCustomers(any(), any())).thenReturn(60L, 50L);
        when(ticketRepository.findTopRoutes(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(topRoute(1L, "Hanoi - Da Nang", 30L)));
        when(ticketRepository.findLoyalCustomers(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(loyalCustomer(7L, "Nguyen Van A", 4L, "1800000")));
        when(ticketRepository.findRecentSuccessfulBookings(any(Pageable.class)))
                .thenReturn(List.of(recentBooking()));

        AdminDashboardResponse response = service.getDashboard();

        assertThat(response.period().startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.period().endDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(response.kpis().revenueTrendPercent()).isEqualByComparingTo("25.5");
        assertThat(response.kpis().tripsTrendPercent()).isEqualByComparingTo("8.0");
        assertThat(response.kpis().ticketsTrendPercent()).isEqualByComparingTo("20.0");
        assertThat(response.kpis().customersTrendPercent()).isEqualByComparingTo("20.0");
        assertThat(response.topRoutes().getFirst().sharePercent()).isEqualByComparingTo("25.0");
        assertThat(response.loyalCustomers().getFirst().rank()).isEqualTo(1);
        assertThat(response.recentBookings()).hasSize(1);
    }

    @Test
    void getDashboardReturnsNullTrendWhenPreviousValueIsZero() {
        when(ticketRepository.sumSuccessfulTicketRevenue(any(), any()))
                .thenReturn(BigDecimal.TEN, BigDecimal.ZERO);
        when(tripRepository.countCompletedTrips(any(), any())).thenReturn(1L, 0L);
        when(ticketRepository.countSuccessfulTickets(any(), any())).thenReturn(1L, 0L);
        when(ticketRepository.countActiveCustomers(any(), any())).thenReturn(1L, 0L);
        when(ticketRepository.findTopRoutes(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(ticketRepository.findLoyalCustomers(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(ticketRepository.findRecentSuccessfulBookings(any(Pageable.class))).thenReturn(List.of());

        AdminDashboardResponse.Kpis kpis = service.getDashboard().kpis();

        assertThat(kpis.revenueTrendPercent()).isNull();
        assertThat(kpis.tripsTrendPercent()).isNull();
        assertThat(kpis.ticketsTrendPercent()).isNull();
        assertThat(kpis.customersTrendPercent()).isNull();
    }

    @Test
    void getRevenueTrendReturnsSixChronologicalMonthlyBuckets() {
        when(ticketRepository.sumSuccessfulTicketRevenue(any(), any())).thenReturn(BigDecimal.ONE);

        List<RevenueTrendResponse> trend = service.getRevenueTrend("monthly");

        assertThat(trend).hasSize(6);
        assertThat(trend.getFirst().label()).isEqualTo("JAN");
        assertThat(trend.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(trend.getLast().label()).isEqualTo("JUN");
        assertThat(trend.getLast().endDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void getRevenueTrendReturnsMondayToSundayWeeklyBuckets() {
        when(ticketRepository.sumSuccessfulTicketRevenue(any(), any())).thenReturn(BigDecimal.ONE);

        List<RevenueTrendResponse> trend = service.getRevenueTrend("weekly");

        assertThat(trend).hasSize(6);
        assertThat(trend.getLast().startDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(trend.getLast().endDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(trend).isSortedAccordingTo(
                (left, right) -> left.startDate().compareTo(right.startDate())
        );
    }

    @Test
    void getRevenueTrendRejectsUnsupportedPeriod() {
        assertThatThrownBy(() -> service.getRevenueTrend("daily"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period must be either weekly or monthly");
    }

    @Test
    void springCreatesServiceWithRepositoryConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TicketRepository.class, () -> ticketRepository);
            context.registerBean(TripRepository.class, () -> tripRepository);
            context.register(AdminDashboardService.class);
            context.refresh();

            assertThat(context.getBean(AdminDashboardService.class)).isNotNull();
        }
    }

    private DashboardTopRouteProjection topRoute(
            Long routeId,
            String routeName,
            Long ticketsSold
    ) {
        return new DashboardTopRouteProjection() {
            public Long getRouteId() {
                return routeId;
            }

            public String getRouteName() {
                return routeName;
            }

            public Long getTicketsSold() {
                return ticketsSold;
            }
        };
    }

    private DashboardLoyalCustomerProjection loyalCustomer(
            Long customerId,
            String customerName,
            Long trips,
            String totalSpent
    ) {
        return new DashboardLoyalCustomerProjection() {
            public Long getCustomerId() {
                return customerId;
            }

            public String getCustomerName() {
                return customerName;
            }

            public Long getTrips() {
                return trips;
            }

            public BigDecimal getTotalSpent() {
                return new BigDecimal(totalSpent);
            }
        };
    }

    private DashboardRecentBookingProjection recentBooking() {
        return new DashboardRecentBookingProjection() {
            public Long getTicketId() {
                return 2849L;
            }

            public String getCustomerName() {
                return "Pham Minh D";
            }

            public String getRouteName() {
                return "Hanoi - Da Nang";
            }

            public Integer getSeatNumber() {
                return 12;
            }

            public BigDecimal getAmount() {
                return new BigDecimal("450000");
            }

            public LocalDateTime getOccurredAt() {
                return LocalDateTime.of(2026, 6, 11, 10, 15);
            }
        };
    }
}
