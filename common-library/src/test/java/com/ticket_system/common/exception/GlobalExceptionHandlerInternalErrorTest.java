package com.ticket_system.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** B18: an unexpected error is logged server-side and never echoes its message to the caller. */
class GlobalExceptionHandlerInternalErrorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ticket/confirm");

    @Test
    void runtimeExceptionMessageIsNotEchoed() {
        ResponseEntity<Object> response = handler.handleRuntimeException(
                new RuntimeException("Table 'quan-ly-ban-hang.tickets' doesn't exist"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(response))
                .containsEntry("message", GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE)
                .containsEntry("path", "/api/ticket/confirm");
    }

    @Test
    void checkedExceptionMessageIsNotEchoed() {
        ResponseEntity<Object> response = handler.handleAllExceptions(
                new java.io.IOException("/srv/uploads/secret.xlsx: permission denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(response)).containsEntry("message", GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE);
    }

    @Test
    void domainExceptionsKeepTheirStatusAndMessage() {
        // Lead review 1.3 L25: messages meant for the user travel as domain exceptions, not as a 500.
        ResponseEntity<Object> notFound = handler.handleResourceNotFound(
                new ResourceNotFoundException("Không tìm thấy phần thưởng: 9"), request);
        ResponseEntity<Object> conflict = handler.handleConflict(
                new ConflictException("Đã tính lương cho d@test.vn này"), request);

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(notFound)).containsEntry("message", "Không tìm thấy phần thưởng: 9");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(conflict)).containsEntry("message", "Đã tính lương cho d@test.vn này");
    }

    @Test
    void lockFailureStays409WithoutTheDatabaseMessage() {
        ResponseEntity<Object> response = handler.handleLockFailure(
                new CannotAcquireLockException("Lock wait timeout exceeded; try restarting transaction"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(response).get("message")).asString().doesNotContain("Lock wait");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<Object> response) {
        return (Map<String, Object>) response.getBody();
    }
}
