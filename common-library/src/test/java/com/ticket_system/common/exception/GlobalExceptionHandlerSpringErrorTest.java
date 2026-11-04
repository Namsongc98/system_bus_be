package com.ticket_system.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Errors raised by Spring MVC itself (not by our code) go through ResponseEntityExceptionHandler.
 * They must use the same body as every other error — with `message` — not a ProblemDetail.
 */
class GlobalExceptionHandlerSpringErrorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ResponseEntity<Object> handle(Exception ex) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/profile/avatar");
        return handler.handleException(ex, new ServletWebRequest(request, new MockHttpServletResponse()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(Map.class);
        return (Map<String, Object>) response.getBody();
    }

    private static void assertStandardBody(ResponseEntity<Object> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(body(response))
                .containsEntry("status", status.value())
                .containsEntry("error", status.getReasonPhrase())
                .containsEntry("message", message)
                .containsEntry("path", "/api/profile/avatar")
                .containsKey("timestamp")
                .doesNotContainKeys("type", "title", "detail", "instance");
    }

    @Test
    void uploadTooLargeIs413WithMessage() throws Exception {
        assertStandardBody(handle(new MaxUploadSizeExceededException(2 * 1024 * 1024)),
                HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép");
    }

    @Test
    void missingMultipartPartIs400NamingThePart() throws Exception {
        assertStandardBody(handle(new MissingServletRequestPartException("file")),
                HttpStatus.BAD_REQUEST, "Thiếu phần 'file' trong form");
    }

    @Test
    void unsupportedContentTypeIs415AndKeepsAcceptHeader() throws Exception {
        ResponseEntity<Object> response = handle(new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_JSON, List.of(MediaType.MULTIPART_FORM_DATA)));

        assertStandardBody(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng nội dung không được hỗ trợ");
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.MULTIPART_FORM_DATA);
    }

    @Test
    void otherClientErrorsUseSpringsDetailAsMessage() throws Exception {
        ResponseEntity<Object> missingParam = handle(new MissingServletRequestParameterException("page", "int"));
        assertThat(missingParam.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(missingParam).get("message")).asString().contains("page")
                .doesNotContain("Exception");
        assertThat(body(missingParam)).doesNotContainKeys("type", "title", "detail");

        ResponseEntity<Object> noRoute = handle(new NoResourceFoundException(HttpMethod.GET, "api/nope"));
        assertThat(noRoute.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(noRoute).get("message")).asString().contains("api/nope");
    }

    @Test
    void serverErrorsNeverEchoTheirDetail() throws Exception {
        // B18: a 5xx handled by Spring itself must look like every other internal error.
        ResponseEntity<Object> response = handle(new ConversionNotSupportedException(
                "secret-internal-value", Integer.class, new IllegalStateException("boom")));

        assertStandardBody(response, HttpStatus.INTERNAL_SERVER_ERROR, GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE);
        assertThat(body(response).toString()).doesNotContain("secret-internal-value");
    }

    @Test
    void committedResponseIsLeftAlone() throws Exception {
        MockHttpServletResponse committed = new MockHttpServletResponse();
        committed.setCommitted(true);

        ResponseEntity<Object> response = handler.handleException(new MissingServletRequestPartException("file"),
                new ServletWebRequest(new MockHttpServletRequest("PUT", "/api/profile/avatar"), committed));

        assertThat(response).isNull();
    }

    @Test
    void nonServletRequestGetsBodyWithoutPath() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new MissingServletRequestPartException("file"), mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response)).containsEntry("message", "Thiếu phần 'file' trong form").containsEntry("path", null);
    }

    @Test
    void unsupportedMethodIs405AndKeepsAllowHeader() throws Exception {
        ResponseEntity<Object> response = handle(new HttpRequestMethodNotSupportedException("POST", List.of("GET", "PUT")));

        assertStandardBody(response, HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ALLOW)).contains("GET", "PUT");
    }
}
