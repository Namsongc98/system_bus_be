package com.ticket_system.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerConflictTest {

    @Test
    void conflictExceptionMapsTo409WithStandardBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ticket");

        ResponseEntity<Object> response = new GlobalExceptionHandler()
                .handleConflict(new ConflictException("Ghế 5 đã có người đặt"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("status", 409)
                .containsEntry("error", "Conflict")
                .containsEntry("message", "Ghế 5 đã có người đặt")
                .containsEntry("path", "/api/ticket")
                .containsKey("timestamp");
    }
}
