package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserCountsResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserResponse;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives UserController through the real AuthInterceptor with real tokens: every endpoint is
 * ADMIN-only, a locked or deleted account is refused even with a valid token (spec 1.2 D3),
 * and bad input is a 400 with the standard error body.
 */
class UserControllerAuthTest {
    // Dummy 32-byte signing key for test tokens only.
    private static final String TEST_SIGNING_KEY = "0123456789".repeat(4).substring(0, 32);
    private static final long ADMIN_ID = 1L;
    private static final String CREATE_BODY = """
            {"email":"tai.xe@test.vn","password":"secret1","role":"DRIVER","fullName":"Tài Xế","phone":"0901"}""";
    private static final String UPDATE_BODY = """
            {"fullName":"Tài Xế","phone":"0901","role":"COLLECTOR"}""";
    private static final String STATUS_BODY = """
            {"active":false}""";
    private static final UserResponse USER = new UserResponse(
            5L, "u@test.vn", UserRole.DRIVER, true, "Tài Xế", "0901", null, LocalDateTime.now(), LocalDateTime.now());

    private final JwtUtil jwtUtil = new JwtUtil(TEST_SIGNING_KEY);
    private final UserService userService = mock(UserService.class);
    private final AtomicReference<AccountState> accountState = new AtomicReference<>(AccountState.active(null));
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(userService))
            .addInterceptors(new AuthInterceptor(jwtUtil, new ObjectMapper(), userId -> accountState.get()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("GET list", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/user"), 200),
                Arguments.of("GET counts", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/user/counts"), 200),
                Arguments.of("GET one", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/user/5"), 200),
                Arguments.of("POST", (Supplier<MockHttpServletRequestBuilder>) () -> post("/api/user")
                        .contentType("application/json").content(CREATE_BODY), 201),
                Arguments.of("PUT", (Supplier<MockHttpServletRequestBuilder>) () -> put("/api/user/5")
                        .contentType("application/json").content(UPDATE_BODY), 200),
                Arguments.of("PUT status", (Supplier<MockHttpServletRequestBuilder>) () -> put("/api/user/5/status")
                        .contentType("application/json").content(STATUS_BODY), 200)
        );
    }

    static Stream<Arguments> endpointsForEachNonAdminRole() {
        return endpoints().flatMap(endpoint -> Stream.of(UserRole.values())
                .filter(role -> role != UserRole.ADMIN)
                .map(role -> Arguments.of(endpoint.get()[0], endpoint.get()[1], role)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void rejectsMissingToken(String name, Supplier<MockHttpServletRequestBuilder> request, int ignored) throws Exception {
        mockMvc.perform(request.get()).andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @ParameterizedTest(name = "{0} as {2}")
    @MethodSource("endpointsForEachNonAdminRole")
    void rejectsNonAdminToken(String name, Supplier<MockHttpServletRequestBuilder> request, UserRole role)
            throws Exception {
        mockMvc.perform(request.get().header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void allowsAdminToken(String name, Supplier<MockHttpServletRequestBuilder> request, int expected) throws Exception {
        stubService();

        mockMvc.perform(request.get().header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void lockedAdminIsForbiddenEvenWithValidToken(String name, Supplier<MockHttpServletRequestBuilder> request,
                                                  int ignored) throws Exception {
        accountState.set(AccountState.locked(UserRole.ADMIN));

        mockMvc.perform(request.get().header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị khoá"));

        verifyNoInteractions(userService);
    }

    @Test
    void demotedAdminIsForbiddenWithTheirOldAdminToken() throws Exception {
        // The token still says ADMIN, the database now says CUSTOMER: the database wins.
        accountState.set(AccountState.active(UserRole.CUSTOMER));

        mockMvc.perform(get("/api/user").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void updateWithInvalidBodyListsFieldErrors() throws Exception {
        mockMvc.perform(put("/api/user/5").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content("{\"fullName\":\"\",\"phone\":\"012345678901234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.role").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void unknownRoleInBodyOrNonNumericIdIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/user/5").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content("{\"fullName\":\"A\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/abc").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/user/5/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content("{\"active\":\"yes\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void serviceConflictAndNotFoundMapTo409And404() throws Exception {
        when(userService.setActive(anyLong(), anyBoolean(), eq(ADMIN_ID)))
                .thenThrow(new ConflictException("Không thể khoá quản trị viên cuối cùng đang hoạt động"));
        when(userService.getUser(99L)).thenThrow(new ResourceNotFoundException("Không tìm thấy người dùng"));

        mockMvc.perform(put("/api/user/5/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(STATUS_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không thể khoá quản trị viên cuối cùng đang hoạt động"));
        mockMvc.perform(get("/api/user/99").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedAccountIsUnauthorized() throws Exception {
        accountState.set(AccountState.MISSING);

        mockMvc.perform(get("/api/user").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void listPassesFiltersAndDefaults() throws Exception {
        stubService();

        mockMvc.perform(get("/api/user").param("role", "DRIVER").param("keyword", "an")
                        .header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("u@test.vn"))
                .andExpect(jsonPath("$.data.content[0].active").value(true))
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist());

        verify(userService).getUsers(UserRole.DRIVER, "an", 0, 10);
    }

    @Test
    void lowercaseRoleIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/user").param("role", "driver").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void createWithInvalidBodyListsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/user").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"123\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.role").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void statusWithoutActiveIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/user/5/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.active").exists());
    }

    @Test
    void callerIdComesFromTheToken() throws Exception {
        stubService();

        mockMvc.perform(put("/api/user/5/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(STATUS_BODY))
                .andExpect(status().isOk());

        verify(userService).setActive(5L, false, ADMIN_ID);
    }

    private void stubService() {
        when(userService.getUsers(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(USER), 0, 10, 1, 1));
        when(userService.getCounts()).thenReturn(new UserCountsResponse(1, Map.of(UserRole.DRIVER, 1L)));
        when(userService.getUser(anyLong())).thenReturn(USER);
        when(userService.create(any())).thenReturn(USER);
        when(userService.update(anyLong(), any(), eq(ADMIN_ID))).thenReturn(USER);
        when(userService.setActive(anyLong(), anyBoolean(), eq(ADMIN_ID))).thenReturn(USER);
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(ADMIN_ID, role);
    }
}
