package com.ticket_system.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {
    private static final String SECRET = "01234567890123456789012345678901";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAuthorityFromJwtRoleInsteadOfHardcodingAdmin() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(SECRET);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, new ObjectMapper());
        String token = jwtUtil.generateAccessToken(9L, UserRole.CUSTOMER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(9L);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
    }
}
