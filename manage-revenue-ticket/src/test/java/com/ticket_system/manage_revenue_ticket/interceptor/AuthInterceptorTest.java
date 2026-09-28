package com.ticket_system.manage_revenue_ticket.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.PublicApi;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.exception.UnauthorizedRoleException;
import com.ticket_system.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {
    private static final String TOKEN = "valid-token";

    private JwtUtil jwtUtil;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        interceptor = new AuthInterceptor(jwtUtil, new ObjectMapper());
    }

    @Test
    void allowsPublicMethodWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public");

        boolean allowed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler(PublicController.class, "publicEndpoint")
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void honorsRoleRequiredDeclaredOnControllerClass() throws Exception {
        prepareValidToken(UserRole.ADMIN);
        MockHttpServletRequest request = authorizedRequest();

        boolean allowed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler(AdminController.class, "dashboard")
        );

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("id")).isEqualTo(15L);
        assertThat(request.getAttribute("role")).isEqualTo("ADMIN");
    }

    @Test
    void honorsRoleRequiredDeclaredOnMethod() throws Exception {
        prepareValidToken(UserRole.COLLECTOR);

        boolean allowed = interceptor.preHandle(
                authorizedRequest(),
                new MockHttpServletResponse(),
                handler(MethodRoleController.class, "collectorEndpoint")
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void allowsAuthenticatedRequestWhenEndpointHasNoRoleRestriction() throws Exception {
        prepareValidToken(UserRole.CUSTOMER);

        boolean allowed = interceptor.preHandle(
                authorizedRequest(),
                new MockHttpServletResponse(),
                handler(AuthenticatedController.class, "profile")
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void rejectsAuthenticatedUserWithWrongRole() {
        prepareValidToken(UserRole.CUSTOMER);

        assertThatThrownBy(() -> interceptor.preHandle(
                authorizedRequest(),
                new MockHttpServletResponse(),
                handler(AdminController.class, "dashboard")
        ))
                .isInstanceOf(UnauthorizedRoleException.class)
                .hasMessage("Bạn không có quyền truy cập tài nguyên này.");
    }

    @Test
    void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/profile"),
                response,
                handler(AuthenticatedController.class, "profile")
        );

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("Invalid or missing JWT");
    }

    @Test
    void allowsCorsPreflightWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/dashboard");

        boolean allowed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler(AdminController.class, "dashboard")
        );

        assertThat(allowed).isTrue();
    }

    private void prepareValidToken(UserRole role) {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(15L);
        when(jwtUtil.getRoleFromToken(TOKEN)).thenReturn(role.name());
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secured");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private HandlerMethod handler(Class<?> controllerType, String methodName) {
        try {
            Object controller = controllerType.getDeclaredConstructor().newInstance();
            Method method = controllerType.getMethod(methodName);
            return new HandlerMethod(controller, method);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    static class PublicController {
        @PublicApi
        public void publicEndpoint() {
        }
    }

    @RoleRequired(UserRole.ADMIN)
    static class AdminController {
        public void dashboard() {
        }
    }

    static class MethodRoleController {
        @RoleRequired({UserRole.ADMIN, UserRole.COLLECTOR})
        public void collectorEndpoint() {
        }
    }

    static class AuthenticatedController {
        public void profile() {
        }
    }
}
