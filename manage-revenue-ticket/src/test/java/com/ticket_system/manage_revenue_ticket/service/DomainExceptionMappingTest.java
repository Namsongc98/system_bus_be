package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.BaseLoyaltyPointsRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.LoyaltyPointsRequest;
import com.ticket_system.manage_revenue_ticket.entity.LoyaltyReward;
import com.ticket_system.manage_revenue_ticket.entity.Salary;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.BaseLoyaltyPointsRepository;
import com.ticket_system.manage_revenue_ticket.repository.BaseSalaryRepository;
import com.ticket_system.manage_revenue_ticket.repository.LoyaltyPointRepository;
import com.ticket_system.manage_revenue_ticket.repository.LoyaltyRewardRepository;
import com.ticket_system.manage_revenue_ticket.repository.SalaryRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Lead review 1.3 L25 (S27): errors the caller must read are domain exceptions — 404
 * (ResourceNotFoundException) or 409 (ConflictException) through GlobalExceptionHandler — not plain
 * RuntimeExceptions, which B18 now turns into a generic 500. The mapping itself is covered by
 * GlobalExceptionHandlerConflictTest / GlobalExceptionHandlerInternalErrorTest in common-library.
 */
class DomainExceptionMappingTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class BaseSalary {
        @Mock private BaseSalaryRepository baseSalaryRepository;
        @Mock private UserRepository userRepository;
        @InjectMocks private BaseSalaryService service;

        @Test
        void unknownUserIsNotFound() {
            when(userRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.save(9L, BigDecimal.TEN, LocalDate.of(2030, 1, 1)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void unknownBaseSalaryIsNotFound() {
            when(baseSalaryRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateBaseSalary(9L, BigDecimal.TEN))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("9");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class LoyaltyPoints {
        @Mock private LoyaltyPointRepository loyaltyPointRepository;
        @Mock private UserRepository userRepository;
        @InjectMocks private LoyaltyPointsService service;

        @Test
        void unknownHistoryEntryIsNotFound() {
            when(loyaltyPointRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(9L, new LoyaltyPointsRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void unknownCustomerIsNotFound() {
            LoyaltyPointsRequest request = new LoyaltyPointsRequest();
            request.setCustomerId(9L);
            when(userRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class LoyaltyRewards {
        @Mock private LoyaltyRewardRepository rewardRepo;
        @InjectMocks private LoyaltyRewardService service;

        @Test
        void unknownRewardIsNotFound() {
            when(rewardRepo.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(9L, new LoyaltyReward()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("9");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class BaseLoyaltyPoints {
        @Mock private BaseLoyaltyPointsRepository loyaltyRepo;
        @InjectMocks private BaseLoyaltyPointsService service;

        @Test
        void missingConfigurationIsNotFound() {
            when(loyaltyRepo.findActiveByRole(any(), any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.calculatePoints("CUSTOMER", 3))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void unknownConfigurationIsNotFound() {
            when(loyaltyRepo.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(9L, new BaseLoyaltyPointsRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Salaries {
        @Mock private SalaryRepository salaryRepository;
        @Mock private TripRepository tripRepository;
        @Mock private BaseSalaryRepository baseSalaryRepository;
        @Mock private UserRepository userRepository;
        @InjectMocks private SalaryService service;

        @Test
        void salaryAlreadyCalculatedForTheMonthIsConflict() {
            User user = new User();
            user.setId(9L);
            user.setEmail("d@test.vn");
            when(userRepository.findById(9L)).thenReturn(Optional.of(user));
            when(salaryRepository.findByPeriodMonthAndPeriodYearAndUserId((byte) 1, (short) 2030, 9L))
                    .thenReturn(List.of(new Salary()));

            assertThatThrownBy(() -> service.generateMonthlySalariesRoleUser((byte) 1, (short) 2030, 9L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("d@test.vn");
        }

        @Test
        void unknownUserIsNotFound() {
            when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateMonthlySalariesRoleUser((byte) 1, (short) 2030, 9L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void driverMissingDuringMonthlyRunIsNotFound() {
            when(tripRepository.countCompletedTripsByDriver(any(Byte.class), any(Short.class), isNull()))
                    .thenReturn(List.<Object[]>of(new Object[]{9L, 3L}));
            when(userRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateMonthlySalaries((byte) 1, (short) 2030))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
