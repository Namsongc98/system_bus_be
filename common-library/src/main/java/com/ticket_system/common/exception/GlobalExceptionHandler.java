package com.ticket_system.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler  {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    // B18: an unexpected error never echoes its message (SQL, class names, internal ids) to the caller;
    // the stack trace goes to the server log instead.
    static final String INTERNAL_ERROR_MESSAGE = "Lỗi hệ thống, vui lòng thử lại sau";

    private ResponseEntity<Object> buildResponseEntity(HttpStatus status, String message, HttpServletRequest request) {
        return new ResponseEntity<>(errorBody(status, message, request.getRequestURI()), status);
    }

    private static Map<String, Object> errorBody(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }

    // A rejected value echoed back to the caller: printable characters only, capped in length.
    private static String echo(Object value) {
        String text = String.valueOf(value).replaceAll("\\p{Cntrl}", "");
        return text.length() > 50 ? text.substring(0, 50) + "…" : text;
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }

    // ⚠️ 400: @Valid body. Same shape as the other errors so the FE can read `message`;
    // `errors` lists every invalid field.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String message = errors.isEmpty()
                ? "Dữ liệu không hợp lệ"
                : errors.values().iterator().next();
        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, message, pathOf(request));
        body.put("errors", errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // ⚠️ 400: query/path param of the wrong type (e.g. unknown enum value, non-numeric id).
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        String message = "Giá trị '" + echo(ex.getValue()) + "' không hợp lệ cho '" + name + "'";
        return new ResponseEntity<>(errorBody(HttpStatus.BAD_REQUEST, message, pathOf(request)), HttpStatus.BAD_REQUEST);
    }

    // ⚠️ 400: unreadable JSON body, including an unknown enum value in a field.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = "Dữ liệu gửi lên không đúng định dạng";
        if (ex.getCause() instanceof InvalidFormatException invalid && !invalid.getPath().isEmpty()) {
            String field = invalid.getPath().get(invalid.getPath().size() - 1).getFieldName();
            if (field == null) field = "body";
            message = "Giá trị '" + echo(invalid.getValue()) + "' không hợp lệ cho '" + field + "'";
        }
        return new ResponseEntity<>(errorBody(HttpStatus.BAD_REQUEST, message, pathOf(request)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedRoleException.class)
    public ResponseEntity<Object> handleUnauthorizedRoleException(UnauthorizedRoleException ex,HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.FORBIDDEN, ex.getMessage(), request);
//        return ResponseEntity
//                .status(HttpStatus.FORBIDDEN)
//                .body(BaseResponseDto.error(HttpStatus.FORBIDDEN.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, HttpServletRequest request) {
        return internalError(ex, request);
    }

    // 💥 500: Lỗi hệ thống
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        return internalError(ex, request);
    }

    private ResponseEntity<Object> internalError(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request);
    }

    // ❌ 404: Không tìm thấy tài nguyên
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object>handleNotFound(NoSuchElementException ex, HttpServletRequest request){
        return buildResponseEntity(HttpStatus.NOT_FOUND, ex.getMessage(), request);
//        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
//        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(BaseResponseDto.error(HttpStatus.NOT_FOUND.value(), message));
    }
    // ⚠️ 400: Dữ liệu không hợp lệ
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.BAD_REQUEST, ex.getMessage(),request);
//        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
//        return ResponseEntity
//                .badRequest()
//                .body(BaseResponseDto.error(HttpStatus.BAD_REQUEST.value(), message));
    }
    // 🚫 403: Không có quyền
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.FORBIDDEN, ex.getMessage(),  request);
//        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
//        return ResponseEntity
//                .status(HttpStatus.FORBIDDEN)
//                .body(BaseResponseDto.error(HttpStatus.FORBIDDEN.value(), "Access Denied: " + message));
    }

    // 🔒 401: Chưa đăng nhập
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Object> handleSecurityException(SecurityException ex,HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.UNAUTHORIZED, ex.getMessage(),  request);
//        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
//        return ResponseEntity
//                .status(HttpStatus.UNAUTHORIZED)
//                .body(BaseResponseDto.error(HttpStatus.UNAUTHORIZED.value(), message));
    }

    // ⛔ 409: Xung đột trạng thái (ghế đã có người, chuyến hết chỗ)
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ⛔ 409: deadlock / lock wait timeout between two writers on the same rows (row locks, task 1.3).
    // The database message is not echoed; the caller is asked to retry.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleLockFailure(PessimisticLockingFailureException ex, HttpServletRequest request) {
        // B36 c: frequent lock failures point at a hot row or a lock-order bug, so they are logged.
        log.warn("Lock failure on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return buildResponseEntity(HttpStatus.CONFLICT,
                "Dữ liệu đang được người khác cập nhật, vui lòng thử lại", request);
    }

    // 🚫 403: tài khoản bị admin khoá
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex,HttpServletRequest request) {
     return    buildResponseEntity(HttpStatus.NOT_FOUND, ex.getMessage(), request);
//        return ResponseEntity
//                .status(HttpStatus.NOT_FOUND)
//                .body(BaseResponseDto.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

}
