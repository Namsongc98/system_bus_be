package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.CurrentUserResponse;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final AuthService authService = new AuthService(userRepository, profileRepository);

    @Test
    void registerIgnoresAdminRoleFromRequestBody() {
        when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User registered = authService.register(UserRequestDto.builder()
                .email("attacker@example.com")
                .password("password123")
                .role(UserRole.ADMIN)
                .build());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(registered.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void registerStoresCustomerWhenNoRoleRequested() {
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User registered = authService.register(UserRequestDto.builder()
                .email("customer@example.com")
                .password("password123")
                .build());

        assertThat(registered.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void registerHashesPasswordInsteadOfStoringPlaintext() {
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User registered = authService.register(UserRequestDto.builder()
                .email("customer@example.com")
                .password("password123")
                .build());

        assertThat(registered.getPassword()).isNotEqualTo("password123");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(UserRequestDto.builder()
                .email("taken@example.com")
                .password("password123")
                .build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCurrentUserCombinesUserAndProfile() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(existingUser(5L, UserRole.ADMIN)));
        when(profileRepository.findByUserId(5L)).thenReturn(Optional.of(
                Profile.builder().fullName("Tran Thi B").phone("0911222333").build()));

        CurrentUserResponse me = authService.getCurrentUser(5L);

        assertThat(me).isEqualTo(new CurrentUserResponse(5L, "user5@example.com", "ADMIN", "Tran Thi B", "0911222333"));
    }

    @Test
    void getCurrentUserWithoutProfileHasNullNameAndPhone() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(existingUser(5L, UserRole.CUSTOMER)));
        when(profileRepository.findByUserId(5L)).thenReturn(Optional.empty());

        CurrentUserResponse me = authService.getCurrentUser(5L);

        assertThat(me).isEqualTo(new CurrentUserResponse(5L, "user5@example.com", "CUSTOMER", null, null));
    }

    @Test
    void getCurrentUserThrowsNotFoundForMissingUser() {
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User existingUser(Long id, UserRole role) {
        User u = User.builder().email("user" + id + "@example.com").password("hash").role(role).build();
        u.setId(id);
        return u;
    }
}
