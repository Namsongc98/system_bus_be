package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.manage_revenue_ticket.projection.CustomerRevenueByRouteProjection;
import com.ticket_system.manage_revenue_ticket.projection.DashboardLoyalCustomerProjection;
import com.ticket_system.manage_revenue_ticket.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RevenueService {
    @Autowired
    public RevenueRepository revenueRepository;

    @Autowired
    public BusesRepository busesRepository;

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public TicketRepository ticketRepository;

    @Autowired
    public SalaryRepository salaryRepository;

    public List<Map<String, Object>> getRevenueByBusAndDate(Long busId, LocalDate date) {
        int year = date.getYear();     // Lấy năm
        int month = date.getMonthValue(); // Lấy tháng (1–12)
        int day = date.getDayOfMonth();   // Lấy ngày (1–31)
        return busesRepository.findBusRevenueNative(year, month, day, busId);
    }

    public List<Map<String, Object>> getRevenueByEmployeeAndDate(Long busId, LocalDate date) {
        int year = date.getYear();     // Lấy năm
        int month = date.getMonthValue(); // Lấy tháng (1–12)
        int day = date.getDayOfMonth();   // Lấy ngày (1–31)
        return busesRepository.findEmployeeRevenueNative(year, month, day, busId);
    }

    public List<CustomerRevenueByRouteProjection> getRevenueByUserAndDate(Long userId, Integer date, Integer month, Integer year) {
        return   busesRepository.getCustomerRevenueByRouteAndDate(year, month, date, userId);
    }

    public BigDecimal getRevenueByBusAndRange(Long busId, LocalDate start, LocalDate end) {
        return Optional.ofNullable(revenueRepository.getRevenueByBusAndRange(busId, start, end))
                .orElse(BigDecimal.ZERO);
    }

    public List<Map<String, Object>> getRevenueAllBusesInDate(LocalDate date) {
        List<Object[]> result = revenueRepository.getTotalRevenueByAllBusesInDate(date);

        return result.stream().map(row -> Map.of(
                "busId", row[0],
                "plateNumber", row[1],
                "totalRevenue", row[2]
        )).toList();
    }

    @KafkaListener(topics = "order-events", groupId = "revenue-group")
    public void consumerBooking(TicketRequestDto booking) {
      System.out.println("Top pic: order-events, groupId: booking-group service revenue => " + booking);
    // Xử lý logic tính tiền ở đây
    }

    // ── Revenue Reports ─────────────────────────────────────────────────────────

    private LocalDateTime[] resolveRange(String period) {
        LocalDate today = LocalDate.now();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime start =
          switch (period.toLowerCase()) {
            case "weekly"  -> today.minusDays(6).atStartOfDay();
            case "monthly" -> today.minusDays(29).atStartOfDay();
            default        -> today.atStartOfDay();
        };
        return new LocalDateTime[]{start, end};
    }

    public Map<String, Object> getRevenueReport(String period) {
        LocalDateTime[] range = resolveRange(period);

        BigDecimal totalRevenue = Optional.ofNullable(
                ticketRepository.sumSuccessfulTicketRevenue(range[0], range[1])
        ).orElse(BigDecimal.ZERO);
        long totalTickets = ticketRepository.countSuccessfulTickets(range[0], range[1]);

        List<Map<String, Object>> rawBuses = ticketRepository.getTicketSummaryByBusAndTime(null, range[0], range[1]);
        long distinctBusCount = rawBuses.stream()
                .map(b -> b.get("busId"))
                .filter(Objects::nonNull)
                .distinct()
                .count();

        BigDecimal avgPerTrip = totalTickets > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalTickets), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgPerBus = distinctBusCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(distinctBusCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Map<String, Object>> buses = rawBuses.stream().map(b -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busId", b.get("plateNumber"));
            row.put("operator", b.get("driver") != null ? b.get("driver") : b.get("seller"));
            row.put("earnings", b.get("totalPrice"));
            row.put("trips", b.get("totalTicket"));
            row.put("status", "active");
            return row;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRevenue", totalRevenue);
        result.put("totalTickets", totalTickets);
        result.put("avgRevenuePerTrip", avgPerTrip);
        result.put("avgRevenuePerBus", avgPerBus);
        result.put("buses", buses);
        return result;
    }

    public List<Map<String, Object>> getRevenueByRoute(String period) {
        LocalDateTime[] range = resolveRange(period);
        return ticketRepository.findRevenueByRoute(range[0], range[1]).stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", r.get("routeName"));
            item.put("revenue", r.get("totalRevenue"));
            item.put("loadFactor", r.get("ticketsSold"));
            return item;
        }).toList();
    }

    public List<Map<String, Object>> getRevenueByDate(String period) {
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime start = LocalDate.now().minusDays(27).atStartOfDay();

        List<Map<String, Object>> rows = ticketRepository.findDailyRevenueDensity(start, end);

        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        LocalDate d = start.toLocalDate();
        for (int i = 0; i < 28; i++) {
            byDate.put(d, BigDecimal.ZERO);
            d = d.plusDays(1);
        }
        for (Map<String, Object> row : rows) {
            LocalDate date = ((java.sql.Date) row.get("reportDate")).toLocalDate();
            if (byDate.containsKey(date)) {
                byDate.put(date, new BigDecimal(row.get("totalRevenue").toString()));
            }
        }

        BigDecimal max = byDate.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        if (max.compareTo(BigDecimal.ZERO) == 0) max = BigDecimal.ONE;
        final BigDecimal maxVal = max;

        return byDate.values().stream().map(v -> {
            int density = v.multiply(BigDecimal.valueOf(8))
                    .divide(maxVal, 0, RoundingMode.HALF_UP).intValue();
            return Map.<String, Object>of("value", density);
        }).toList();
    }

    public List<Map<String, Object>> getTopCustomers(String period) {
        LocalDateTime[] range = resolveRange(period);
        List<DashboardLoyalCustomerProjection> raw =
                ticketRepository.findLoyalCustomers(range[0], range[1], PageRequest.of(0, 10));

        if (raw.isEmpty()) return List.of();

        BigDecimal maxSpent = raw.stream()
                .map(DashboardLoyalCustomerProjection::getTotalSpent)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        if (maxSpent.compareTo(BigDecimal.ZERO) == 0) maxSpent = BigDecimal.ONE;
        final BigDecimal maxVal = maxSpent;

        return raw.stream().map(c -> {
            int score = c.getTotalSpent() != null
                    ? c.getTotalSpent().multiply(BigDecimal.valueOf(100))
                    .divide(maxVal, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", c.getCustomerName());
            item.put("role", "Customer");
            item.put("score", score);
            return item;
        }).toList();
    }
}
