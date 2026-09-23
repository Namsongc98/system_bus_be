package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import com.ticket_system.manage_revenue_ticket.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives AuthController with the real AuthService and AuthInterceptor (only the
 * repository is mocked), so the DoD "register with role=ADMIN stays CUSTOMER" is
 * checked on the HTTP response and the issued JWT, not only in the service.
 */
class AuthControllerTest {
    private static final String SECRET = "01234567890123456789012345678901";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(
                    new AuthService(userRepository, profileRepository), jwtUtil, mock(RedisTemplate.class)))
            .addInterceptors(new AuthInterceptor(jwtUtil, mapper))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void registerWithAdminRoleInBodyIssuesCustomerToken() throws Exception {
        when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"attacker@example.com\",\"password\":\"secret123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);

        JsonNode data = mapper.readTree(body).path("data");
        assertThat(jwtUtil.getRoleFromToken(data.path("accessToken").asText())).isEqualTo("CUSTOMER");
        assertThat(jwtUtil.getRoleFromToken(data.path("refreshToken").asText())).isEqualTo("CUSTOMER");
    }

    @Test
    void updatePasswordRequiresToken() throws Exception {
        mockMvc.perform(put("/api/auth/update-password")
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"old-pass\",\"password\":\"new-pass\"}"))
                .andExpect(status().isUnauthorized());

        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePasswordChangesCallerAccountAndIgnoresEmailInBody() throws Exception {
        User caller = user(7L, "caller@example.com", "old-pass");
        when(userRepository.findById(7L)).thenReturn(Optional.of(caller));

        mockMvc.perform(put("/api/auth/update-password")
                        .header("Authorization", bearer(7L, UserRole.CUSTOMER))
                        .contentType("application/json")
                        .content("{\"email\":\"victim@example.com\",\"oldPassword\":\"old-pass\",\"password\":\"new-pass\"}"))
                .andExpect(status().isOk());

        verify(userRepository, never()).findByEmail(anyString());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(7L);
        assertThat(encoder.matches("new-pass", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void updatePasswordRejectsWrongOldPassword() throws Exception {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "caller@example.com", "old-pass")));

        mockMvc.perform(put("/api/auth/update-password")
                        .header("Authorization", bearer(7L, UserRole.CUSTOMER))
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"wrong-pass\",\"password\":\"new-pass\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePasswordValidatesBody() throws Exception {
        mockMvc.perform(put("/api/auth/update-password")
                        .header("Authorization", bearer(7L, UserRole.CUSTOMER))
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"old-pass\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).findById(any());
    }

    @Test
    void meReturnsCallerFromJwtWithProfile() throws Exception {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "caller@example.com", "old-pass")));
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(
                Profile.builder().fullName("Nguyen Van A").phone("0901234567").build()));

        String body = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(7L, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = mapper.readTree(body).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(7L);
        assertThat(data.path("email").asText()).isEqualTo("caller@example.com");
        assertThat(data.path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(data.path("fullName").asText()).isEqualTo("Nguyen Van A");
        assertThat(data.path("phone").asText()).isEqualTo("0901234567");
        assertThat(data.has("password")).isFalse();
    }

    @Test
    void meReturnsNullNameAndPhoneWithoutProfile() throws Exception {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "caller@example.com", "old-pass")));
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());

        String body = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(7L, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = mapper.readTree(body).path("data");
        assertThat(data.path("email").asText()).isEqualTo("caller@example.com");
        assertThat(data.path("fullName").isNull()).isTrue();
        assertThat(data.path("phone").isNull()).isTrue();
    }

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(userRepository, never()).findById(any());
    }

    @Test
    void meRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        verify(userRepository, never()).findById(any());
    }

    @Test
    void meReturnsNotFoundWhenJwtUserNoLongerExists() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(99L, UserRole.CUSTOMER)))
                .andExpect(status().isNotFound());
    }

    private User user(Long id, String email, String rawPassword) {
        User u = User.builder().email(email).password(encoder.encode(rawPassword)).role(UserRole.CUSTOMER).build();
        u.setId(id);
        return u;
    }

    private String bearer(Long userId, UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(userId, role);
    }
}
