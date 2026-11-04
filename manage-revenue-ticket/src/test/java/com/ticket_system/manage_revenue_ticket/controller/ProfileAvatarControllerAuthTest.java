package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.Dto.response.AvatarResponse;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.AvatarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives ProfileAvatarController through the real AuthInterceptor with real tokens: any logged-in
 * role may manage its own avatar, the user id always comes from the JWT, and errors keep the
 * standard error body.
 */
class ProfileAvatarControllerAuthTest {
    // Dummy 32-byte signing key for test tokens only.
    private static final String TEST_SIGNING_KEY = "0123456789".repeat(4).substring(0, 32);
    private static final long USER_ID = 42L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final JwtUtil jwtUtil = new JwtUtil(TEST_SIGNING_KEY);
    private final AvatarService avatarService = mock(AvatarService.class);
    private final AtomicReference<AccountState> accountState = new AtomicReference<>(AccountState.active(null));
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProfileAvatarController(avatarService))
            .addInterceptors(new AuthInterceptor(jwtUtil, new ObjectMapper(), userId -> accountState.get()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/profile/avatar")).andExpect(status().isUnauthorized());

        verifyNoInteractions(avatarService);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void everyRoleUploadsUnderTheIdFromItsToken(UserRole role) throws Exception {
        when(avatarService.upload(eq(USER_ID), any())).thenReturn(new AvatarResponse("image/png", PNG.length));

        mockMvc.perform(upload().header("Authorization", bearer(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(PNG.length));

        verify(avatarService).upload(eq(USER_ID), any());
    }

    @Test
    void downloadStreamsTheStoredImage() throws Exception {
        when(avatarService.download(USER_ID)).thenReturn(new AvatarService.Avatar(PNG, "image/png"));

        mockMvc.perform(get("/api/profile/avatar").header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-cache, private"));
    }

    @Test
    void downloadWithoutAvatarIs404() throws Exception {
        when(avatarService.download(USER_ID)).thenThrow(new ResourceNotFoundException("Chưa có ảnh đại diện"));

        mockMvc.perform(get("/api/profile/avatar").header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Chưa có ảnh đại diện"));
    }

    @Test
    void invalidImageIs400WithTheServiceMessage() throws Exception {
        when(avatarService.upload(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("Chỉ nhận ảnh JPEG, PNG hoặc WEBP"));

        mockMvc.perform(upload().header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ nhận ảnh JPEG, PNG hoặc WEBP"));
    }

    @Test
    void missingFilePartIs400() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/profile/avatar")
                        .header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thiếu phần 'file' trong form"));

        verifyNoInteractions(avatarService);
    }

    @Test
    void jsonBodyIs415WithStandardErrorBody() throws Exception {
        mockMvc.perform(put("/api/profile/avatar")
                        .header("Authorization", bearer(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Định dạng nội dung không được hỗ trợ"))
                .andExpect(jsonPath("$.title").doesNotExist());

        verifyNoInteractions(avatarService);
    }

    @Test
    void wrongMethodIs405WithAllowHeaderAndStandardErrorBody() throws Exception {
        mockMvc.perform(post("/api/profile/avatar").header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("GET"), org.hamcrest.Matchers.containsString("PUT"))))
                .andExpect(jsonPath("$.message").value("Phương thức không được hỗ trợ"))
                .andExpect(jsonPath("$.title").doesNotExist());

        verifyNoInteractions(avatarService);
    }

    @Test
    void uploadOverTheMultipartLimitIs413WithStandardErrorBody() throws Exception {
        // MockMvc does not enforce spring.servlet.multipart.max-file-size; the container throws this.
        when(avatarService.upload(eq(USER_ID), any())).thenThrow(new MaxUploadSizeExceededException(2 * 1024 * 1024));

        mockMvc.perform(upload().header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Tệp vượt quá dung lượng cho phép"))
                .andExpect(jsonPath("$.title").doesNotExist());
    }

    @Test
    void lockedAccountIsForbiddenEvenWithValidToken() throws Exception {
        accountState.set(AccountState.locked(UserRole.CUSTOMER));

        mockMvc.perform(upload().header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(avatarService);
    }

    private static MockMultipartHttpServletRequestBuilder upload() {
        return multipart(HttpMethod.PUT, "/api/profile/avatar")
                .file(new MockMultipartFile("file", "a.png", "image/png", PNG));
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(USER_ID, role);
    }
}
