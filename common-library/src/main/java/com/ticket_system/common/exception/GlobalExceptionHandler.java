package com.ticket_system.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class GlobalExceptionHandler extends ResponseEntityExceptionHandler  {

    private ResponseEntity<Object> buildResponseEntity(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now().toString());
        return new ResponseEntity<>(body, status);
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
            return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR,ex.getMessage(),request);
//        return ResponseEntity
//                .status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(BaseResponseDto.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }

    // 💥 500: Lỗi hệ thống
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR,ex.getMessage(), request);
//        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
//        return ResponseEntity
//                .status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(BaseResponseDto.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex,HttpServletRequest request) {
     return    buildResponseEntity(HttpStatus.NOT_FOUND, ex.getMessage(), request);
//        return ResponseEntity
//                .status(HttpStatus.NOT_FOUND)
//                .body(BaseResponseDto.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

}
